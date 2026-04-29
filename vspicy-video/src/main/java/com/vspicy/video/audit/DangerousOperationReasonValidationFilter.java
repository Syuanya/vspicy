package com.vspicy.video.audit;

import com.vspicy.video.service.OperationAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DangerousOperationReasonValidationFilter extends OncePerRequestFilter {
    private static final int MIN_REASON_LENGTH = 5;
    private static final int BODY_SUMMARY_MAX_LENGTH = 2000;

    private final ObjectProvider<OperationAuditService> auditServiceProvider;

    public DangerousOperationReasonValidationFilter(ObjectProvider<OperationAuditService> auditServiceProvider) {
        this.auditServiceProvider = auditServiceProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String uri = request.getRequestURI();
        return uri == null || !uri.contains("/api/videos/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);

        ValidationResult result = validate(wrapped, body);
        if (!result.valid()) {
            recordRejected(wrapped, body, result);
            writeValidationError(response, result.message());
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private ValidationResult validate(HttpServletRequest request, byte[] bodyBytes) {
        String path = normalizedPath(request);

        if (path.contains("/api/videos/admin/operation-audit/")) {
            return ValidationResult.ok();
        }

        String body = bodyString(request, bodyBytes);
        String reason = firstNonBlank(
                request.getParameter("reason"),
                extractJsonString(body, "reason")
        );
        Boolean dryRun = firstBoolean(
                request.getParameter("dryRun"),
                extractJsonRaw(body, "dryRun")
        );

        ReasonRule rule = ruleFor(path);
        if (rule == null) {
            return ValidationResult.ok();
        }

        boolean requireReason = switch (rule.mode()) {
            case ALWAYS -> true;
            case WHEN_DRY_RUN_FALSE -> Boolean.FALSE.equals(dryRun);
            case WHEN_NOT_DRY_RUN_TRUE -> !Boolean.TRUE.equals(dryRun);
        };

        if (!requireReason) {
            return ValidationResult.ok();
        }

        if (!validReason(reason)) {
            return ValidationResult.fail(rule, reason, dryRun, path);
        }

        return ValidationResult.ok();
    }

    private ReasonRule ruleFor(String path) {
        Matcher transcode = Pattern.compile("^/api/videos/transcode/state/(\\d+)/(rerun|cancel|reset|success|fail)$").matcher(path);
        if (transcode.matches()) {
            String op = transcode.group(2);
            String action = switch (op) {
                case "rerun" -> "TRANSCODE_RERUN";
                case "cancel" -> "TRANSCODE_CANCEL";
                case "reset" -> "TRANSCODE_RESET";
                case "success" -> "TRANSCODE_MARK_SUCCESS";
                case "fail" -> "TRANSCODE_MARK_FAILED";
                default -> "TRANSCODE_OPERATION";
            };
            return new ReasonRule(
                    action,
                    "TRANSCODE_TASK",
                    transcode.group(1),
                    ReasonMode.ALWAYS,
                    "高危转码操作必须填写 reason，且至少 " + MIN_REASON_LENGTH + " 个字符"
            );
        }

        Matcher singlePlayback = Pattern.compile("^/api/videos/playback/readiness/(\\d+)/sync$").matcher(path);
        if (singlePlayback.matches()) {
            return new ReasonRule(
                    "PLAYBACK_READINESS_SYNC",
                    "VIDEO",
                    singlePlayback.group(1),
                    ReasonMode.WHEN_DRY_RUN_FALSE,
                    "正式同步播放就绪状态必须填写 reason，且至少 " + MIN_REASON_LENGTH + " 个字符"
            );
        }

        if (path.equals("/api/videos/playback/readiness-batch/sync")) {
            return new ReasonRule(
                    "PLAYBACK_READINESS_BATCH_SYNC",
                    "VIDEO_BATCH",
                    "batch",
                    ReasonMode.WHEN_DRY_RUN_FALSE,
                    "正式批量同步播放就绪状态必须填写 reason，且至少 " + MIN_REASON_LENGTH + " 个字符"
            );
        }

        if (path.contains("/hls") && path.contains("/repair")) {
            return new ReasonRule(
                    "HLS_REPAIR_OPERATION",
                    "HLS_REPAIR",
                    extractLastNumber(path, "unknown"),
                    ReasonMode.WHEN_NOT_DRY_RUN_TRUE,
                    "HLS 修复运维操作必须填写 reason，且至少 " + MIN_REASON_LENGTH + " 个字符"
            );
        }

        if (path.contains("/object") && path.contains("/cleanup")) {
            return new ReasonRule(
                    "CLEANUP_OPERATION",
                    "OBJECT_CLEANUP",
                    extractLastNumber(path, "unknown"),
                    ReasonMode.WHEN_NOT_DRY_RUN_TRUE,
                    "对象清理运维操作必须填写 reason，且至少 " + MIN_REASON_LENGTH + " 个字符"
            );
        }

        if (path.contains("/storage") && (path.contains("/cleanup") || path.contains("/delete") || path.contains("/execute"))) {
            return new ReasonRule(
                    "STORAGE_OPERATION",
                    "STORAGE",
                    extractLastNumber(path, "unknown"),
                    ReasonMode.WHEN_NOT_DRY_RUN_TRUE,
                    "存储危险操作必须填写 reason，且至少 " + MIN_REASON_LENGTH + " 个字符"
            );
        }

        return null;
    }

    private void recordRejected(HttpServletRequest request, byte[] bodyBytes, ValidationResult result) {
        try {
            OperationAuditService auditService = auditServiceProvider.getIfAvailable();
            if (auditService == null || result.rule() == null) {
                return;
            }

            String body = bodyString(request, bodyBytes);
            String detailJson = rejectedDetailJson(request, body, result);

            auditService.recordQuietly(
                    result.rule().action() + "_REJECTED",
                    result.rule().targetType(),
                    result.rule().targetId(),
                    operatorId(request),
                    operatorName(request),
                    "高危操作被拒绝：" + result.message(),
                    detailJson
            );
        } catch (Exception ignored) {
            // 失败审计不能影响校验响应
        }
    }

    private String rejectedDetailJson(HttpServletRequest request, String body, ValidationResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("method", request.getMethod());
        map.put("path", normalizedPath(request));
        map.put("query", request.getQueryString());
        map.put("status", 400);
        map.put("rejected", true);
        map.put("validationMessage", result.message());

        if (!isBlank(result.reason())) {
            map.put("reason", result.reason());
        }

        if (result.dryRun() != null) {
            map.put("dryRun", result.dryRun());
        }

        if (!isBlank(body)) {
            map.put("bodySummary", truncate(sanitize(body.trim()), BODY_SUMMARY_MAX_LENGTH));
        }

        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("\"").append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(builder, entry.getValue());
        }
        builder.append("}");
        return builder.toString();
    }

    private void appendJsonValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }

        if (value instanceof Boolean || value instanceof Number) {
            builder.append(value);
            return;
        }

        builder.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
    }

    private boolean validReason(String reason) {
        return reason != null && reason.trim().length() >= MIN_REASON_LENGTH;
    }

    private void writeValidationError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");

        String body = """
                {"code":400,"message":"%s","data":null}
                """.formatted(escapeJson(message));

        response.getWriter().write(body);
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }

        return uri;
    }

    private String bodyString(HttpServletRequest request, byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "";
        }

        Charset charset;
        try {
            charset = Charset.forName(request.getCharacterEncoding());
        } catch (Exception ex) {
            charset = StandardCharsets.UTF_8;
        }

        return new String(bodyBytes, charset);
    }

    private String extractJsonString(String body, String field) {
        if (body == null || body.isBlank()) {
            return null;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        return unescapeJsonString(matcher.group(1));
    }

    private String extractJsonRaw(String body, String field) {
        if (body == null || body.isBlank()) {
            return null;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false|null|-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1);
        return "null".equalsIgnoreCase(value) ? null : value;
    }

    private Boolean firstBoolean(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            if ("true".equalsIgnoreCase(value)) {
                return true;
            }

            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }

        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Long operatorId(HttpServletRequest request) {
        String value = firstHeader(request, "X-User-Id", "X-Operator-Id");
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String operatorName(HttpServletRequest request) {
        String value = firstHeader(request, "X-Username", "X-Operator-Name");
        if (value == null || value.isBlank()) {
            return "system";
        }
        return value;
    }

    private String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String extractLastNumber(String path, String fallback) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(path);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last == null ? fallback : last;
    }

    private String sanitize(String body) {
        String result = body;

        String[] fields = {
                "password",
                "secret",
                "token",
                "accessKey",
                "secretKey",
                "authorization"
        };

        for (String field : fields) {
            result = result.replaceAll(
                    "(?i)(\"" + Pattern.quote(field) + "\"\\s*:\\s*\")((?:\\\\.|[^\"])*)\"",
                    "$1****\""
            );
        }

        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String unescapeJsonString(String value) {
        if (value == null) {
            return null;
        }

        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private enum ReasonMode {
        ALWAYS,
        WHEN_DRY_RUN_FALSE,
        WHEN_NOT_DRY_RUN_TRUE
    }

    private record ReasonRule(
            String action,
            String targetType,
            String targetId,
            ReasonMode mode,
            String message
    ) {
    }

    private record ValidationResult(
            boolean valid,
            String message,
            ReasonRule rule,
            String reason,
            Boolean dryRun
    ) {
        static ValidationResult ok() {
            return new ValidationResult(true, null, null, null, null);
        }

        static ValidationResult fail(ReasonRule rule, String reason, Boolean dryRun, String path) {
            return new ValidationResult(false, rule.message(), rule, reason, dryRun);
        }
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody == null ? new byte[0] : cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);

            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    if (listener == null) {
                        return;
                    }

                    try {
                        listener.onDataAvailable();
                        if (isFinished()) {
                            listener.onAllDataRead();
                        }
                    } catch (IOException ex) {
                        listener.onError(ex);
                    }
                }

                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return inputStream.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset;
            try {
                charset = Charset.forName(getCharacterEncoding());
            } catch (Exception ex) {
                charset = StandardCharsets.UTF_8;
            }

            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
