package com.vspicy.video.audit;

import com.vspicy.video.service.OperationAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OperationAuditAutoRecordInterceptor implements HandlerInterceptor {
    private static final String START_TIME_ATTR = OperationAuditAutoRecordInterceptor.class.getName() + ".START_TIME";
    private static final int BODY_SUMMARY_MAX_LENGTH = 2000;

    private final OperationAuditService operationAuditService;

    public OperationAuditAutoRecordInterceptor(OperationAuditService operationAuditService) {
        this.operationAuditService = operationAuditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        try {
            AuditMatch match = match(request);
            if (match == null) {
                return;
            }

            int status = response.getStatus();
            if (status >= 500) {
                return;
            }

            RequestBodySummary body = requestBodySummary(request);
            Long durationMs = durationMs(request);
            String description = descriptionWithReason(match.description(), body.reason());
            String detailJson = detailJson(request, status, durationMs, body);

            operationAuditService.recordQuietly(
                    match.action(),
                    match.targetType(),
                    match.targetId(),
                    operatorId(request),
                    operatorName(request),
                    description,
                    detailJson
            );
        } catch (Exception ignored) {
            // 审计不能影响主业务
        }
    }

    private AuditMatch match(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return null;
        }

        String path = normalizedPath(request);

        if (path.contains("/api/videos/admin/operation-audit/record")) {
            return null;
        }

        AuditMatch transcodeDispatch = matchRegex(
                path,
                "^/api/videos/transcode/dispatch/(\\d+)$",
                "TRANSCODE_DISPATCH",
                "TRANSCODE_TASK",
                "手动分发转码任务"
        );
        if (transcodeDispatch != null) {
            return transcodeDispatch;
        }

        AuditMatch transcodeDispatchLocal = matchRegex(
                path,
                "^/api/videos/transcode/dispatch/(\\d+)/local$",
                "TRANSCODE_DISPATCH_LOCAL",
                "TRANSCODE_TASK",
                "强制本地分发转码任务"
        );
        if (transcodeDispatchLocal != null) {
            return transcodeDispatchLocal;
        }

        AuditMatch transcodeState = matchTranscodeState(path);
        if (transcodeState != null) {
            return transcodeState;
        }

        AuditMatch playbackSync = matchRegex(
                path,
                "^/api/videos/playback/readiness/(\\d+)/sync$",
                "PLAYBACK_READINESS_SYNC",
                "VIDEO",
                "同步单视频播放就绪状态"
        );
        if (playbackSync != null) {
            return playbackSync;
        }

        if (path.equals("/api/videos/playback/readiness-batch/sync")) {
            return new AuditMatch(
                    "PLAYBACK_READINESS_BATCH_SYNC",
                    "VIDEO_BATCH",
                    "batch",
                    "批量同步播放就绪状态"
            );
        }

        if (path.contains("/hls") && path.contains("/repair")) {
            return new AuditMatch(
                    "HLS_REPAIR_OPERATION",
                    "HLS_REPAIR",
                    extractLastNumber(path, "unknown"),
                    "HLS 修复运维操作"
            );
        }

        if (path.contains("/object") && path.contains("/cleanup")) {
            return new AuditMatch(
                    "CLEANUP_OPERATION",
                    "OBJECT_CLEANUP",
                    extractLastNumber(path, "unknown"),
                    "对象清理运维操作"
            );
        }

        if (path.contains("/storage")) {
            return new AuditMatch(
                    "STORAGE_OPERATION",
                    "STORAGE",
                    extractLastNumber(path, "unknown"),
                    "存储运维操作"
            );
        }

        return null;
    }

    private AuditMatch matchTranscodeState(String path) {
        Pattern pattern = Pattern.compile("^/api/videos/transcode/state/(\\d+)/(retry|rerun|cancel|reset|success|fail)$");
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }

        String id = matcher.group(1);
        String op = matcher.group(2);

        return switch (op) {
            case "retry" -> new AuditMatch("TRANSCODE_RETRY", "TRANSCODE_TASK", id, "重试转码任务");
            case "rerun" -> new AuditMatch("TRANSCODE_RERUN", "TRANSCODE_TASK", id, "强制重跑转码任务");
            case "cancel" -> new AuditMatch("TRANSCODE_CANCEL", "TRANSCODE_TASK", id, "取消转码任务");
            case "reset" -> new AuditMatch("TRANSCODE_RESET", "TRANSCODE_TASK", id, "重置转码任务");
            case "success" -> new AuditMatch("TRANSCODE_MARK_SUCCESS", "TRANSCODE_TASK", id, "手动标记转码成功");
            case "fail" -> new AuditMatch("TRANSCODE_MARK_FAILED", "TRANSCODE_TASK", id, "手动标记转码失败");
            default -> null;
        };
    }

    private AuditMatch matchRegex(
            String path,
            String regex,
            String action,
            String targetType,
            String description
    ) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }

        return new AuditMatch(action, targetType, matcher.group(1), description);
    }

    private RequestBodySummary requestBodySummary(HttpServletRequest request) {
        String body = cachedBody(request);
        if (body == null || body.isBlank()) {
            return RequestBodySummary.empty();
        }

        String sanitized = sanitize(body.trim());
        String summary = truncate(sanitized, BODY_SUMMARY_MAX_LENGTH);

        return new RequestBodySummary(
                summary,
                extractJsonString(body, "reason"),
                extractJsonString(body, "confirmText"),
                extractJsonRaw(body, "dryRun"),
                extractJsonRaw(body, "limit"),
                extractJsonRaw(body, "onlyProblem")
        );
    }

    private String cachedBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }

        byte[] bytes = wrapper.getContentAsByteArray();
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Charset charset;
        try {
            charset = Charset.forName(wrapper.getCharacterEncoding());
        } catch (Exception ex) {
            charset = StandardCharsets.UTF_8;
        }

        return new String(bytes, charset);
    }

    private String detailJson(HttpServletRequest request, int status, Long durationMs, RequestBodySummary body) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("method", request.getMethod());
        map.put("path", normalizedPath(request));
        map.put("query", request.getQueryString());
        map.put("status", status);
        map.put("durationMs", durationMs);

        if (!isBlank(body.reason())) {
            map.put("reason", body.reason());
        }
        if (!isBlank(body.confirmText())) {
            map.put("confirmText", body.confirmText());
        }
        if (!isBlank(body.dryRun())) {
            map.put("dryRun", body.dryRun());
        }
        if (!isBlank(body.limit())) {
            map.put("limit", body.limit());
        }
        if (!isBlank(body.onlyProblem())) {
            map.put("onlyProblem", body.onlyProblem());
        }
        if (!isBlank(body.bodySummary())) {
            map.put("bodySummary", body.bodySummary());
        }

        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":");
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

        String text = String.valueOf(value);

        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            builder.append(text.toLowerCase());
            return;
        }

        if (text.matches("-?\\d+(\\.\\d+)?")) {
            builder.append(text);
            return;
        }

        builder.append("\"").append(escape(text)).append("\"");
    }

    private String descriptionWithReason(String description, String reason) {
        if (isBlank(reason)) {
            return description;
        }
        return description + "；reason=" + reason;
    }

    private String extractJsonString(String body, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        return unescapeJsonString(matcher.group(1));
    }

    private String extractJsonRaw(String body, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false|null|-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1);
        return "null".equalsIgnoreCase(value) ? null : value;
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

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }

        return uri;
    }

    private Long durationMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_TIME_ATTR);
        if (start instanceof Number number) {
            return System.currentTimeMillis() - number.longValue();
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

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AuditMatch(
            String action,
            String targetType,
            String targetId,
            String description
    ) {
    }

    private record RequestBodySummary(
            String bodySummary,
            String reason,
            String confirmText,
            String dryRun,
            String limit,
            String onlyProblem
    ) {
        static RequestBodySummary empty() {
            return new RequestBodySummary(null, null, null, null, null, null);
        }
    }
}
