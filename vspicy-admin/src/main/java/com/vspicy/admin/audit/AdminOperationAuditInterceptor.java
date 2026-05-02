package com.vspicy.admin.audit;

import com.vspicy.admin.dto.OperationAuditCreateCommand;
import com.vspicy.admin.service.OperationAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AdminOperationAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AdminOperationAuditInterceptor.class);
    private static final String START_TIME = AdminOperationAuditInterceptor.class.getName() + ".START_TIME";

    private final OperationAuditService operationAuditService;

    public AdminOperationAuditInterceptor(OperationAuditService operationAuditService) {
        this.operationAuditService = operationAuditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldAudit(request)) {
            request.setAttribute(START_TIME, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object start = request.getAttribute(START_TIME);
        if (!(start instanceof Long startMs)) {
            return;
        }
        try {
            long costMs = Math.max(0, System.currentTimeMillis() - startMs);
            int status = response.getStatus();
            boolean success = ex == null && status < 400;
            String uri = request.getRequestURI();
            String method = request.getMethod();
            String actionType = actionType(method, uri);
            String riskLevel = riskLevel(method, uri, success);
            String traceId = firstHeader(request, "X-Trace-Id", "x-trace-id", "X-Request-Id", "x-request-id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            operationAuditService.record(new OperationAuditCreateCommand(
                    traceId,
                    "vspicy-admin",
                    moduleName(uri),
                    actionType,
                    operationName(method, uri),
                    method,
                    uri,
                    requestParams(request),
                    requestBodySummary(request),
                    status,
                    success,
                    ex == null ? null : ex.getMessage(),
                    riskLevel,
                    operatorId(request),
                    operatorName(request),
                    clientIp(request),
                    truncate(request.getHeader("User-Agent"), 500),
                    costMs
            ));
        } catch (Exception auditEx) {
            log.warn("后台操作审计记录失败: {}", auditEx.getMessage());
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        if (uri == null || !uri.startsWith("/api/admin/")) {
            return false;
        }
        return !uri.startsWith("/api/admin/operation-audit-logs");
    }

    private String actionType(String method, String uri) {
        String lower = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if ("POST".equalsIgnoreCase(method) && (lower.contains("/publish") || lower.contains("/offline") || lower.contains("/resolve") || lower.contains("/close") || lower.contains("/reopen"))) {
            return "STATUS";
        }
        if ("POST".equalsIgnoreCase(method) && (lower.contains("/reply") || lower.contains("/assign") || lower.contains("/handle") || lower.contains("/review") || lower.contains("/ignore"))) {
            return "HANDLE";
        }
        if ("POST".equalsIgnoreCase(method)) {
            return "CREATE";
        }
        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            return "UPDATE";
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return "DELETE";
        }
        return "OTHER";
    }

    private String riskLevel(String method, String uri, boolean success) {
        String lower = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (!success && (lower.contains("permission") || lower.contains("role") || lower.contains("user") || lower.contains("config"))) {
            return "CRITICAL";
        }
        if ("DELETE".equalsIgnoreCase(method) || lower.contains("permission") || lower.contains("role") || lower.contains("cleanup")) {
            return "HIGH";
        }
        if (lower.contains("publish") || lower.contains("offline") || lower.contains("resolve") || lower.contains("close") || lower.contains("assign")) {
            return "MEDIUM";
        }
        return success ? "LOW" : "MEDIUM";
    }

    private String moduleName(String uri) {
        if (uri == null) {
            return "后台管理";
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.contains("video")) return "视频管理";
        if (lower.contains("user")) return "用户管理";
        if (lower.contains("role") || lower.contains("permission")) return "权限管理";
        if (lower.contains("announcement")) return "公告管理";
        if (lower.contains("support-ticket")) return "工单中心";
        if (lower.contains("exception-log")) return "异常日志";
        if (lower.contains("config") || lower.contains("setting")) return "系统配置";
        return "后台管理";
    }

    private String operationName(String method, String uri) {
        return method + " " + uri;
    }

    private String requestParams(HttpServletRequest request) {
        Enumeration<String> names = request.getParameterNames();
        StringBuilder builder = new StringBuilder();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (builder.length() > 0) {
                builder.append('&');
            }
            String values = Arrays.stream(request.getParameterValues(name) == null ? new String[0] : request.getParameterValues(name))
                    .map(value -> isSensitive(name) ? "******" : value)
                    .collect(Collectors.joining(","));
            builder.append(name).append('=').append(values);
        }
        return truncate(builder.toString(), 5000);
    }

    private String requestBodySummary(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("multipart")) {
            return "[multipart skipped]";
        }
        byte[] bytes = wrapper.getContentAsByteArray();
        if (bytes.length == 0) {
            return null;
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        return truncate(maskSensitive(body), 5000);
    }

    private String maskSensitive(String body) {
        if (body == null) {
            return null;
        }
        return body
                .replaceAll("(?i)(\\\"password\\\"\\s*:\\s*)\\\"[^\\\"]*\\\"", "$1\"******\"")
                .replaceAll("(?i)(\\\"token\\\"\\s*:\\s*)\\\"[^\\\"]*\\\"", "$1\"******\"")
                .replaceAll("(?i)(\\\"secret\\\"\\s*:\\s*)\\\"[^\\\"]*\\\"", "$1\"******\"");
    }

    private boolean isSensitive(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("token") || lower.contains("secret");
    }

    private Long operatorId(HttpServletRequest request) {
        String value = firstHeader(request, "X-User-Id", "x-user-id", "X-UserId", "x-userid");
        if (value == null || value.isBlank()) {
            return 1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }

    private String operatorName(HttpServletRequest request) {
        String value = firstHeader(request, "X-Username", "x-username", "X-User-Name", "x-user-name");
        return value == null || value.isBlank() ? "dev-admin" : value.trim();
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

    private String clientIp(HttpServletRequest request) {
        String value = firstHeader(request, "X-Forwarded-For", "x-forwarded-for", "X-Real-IP", "x-real-ip");
        if (value != null && !value.isBlank()) {
            int comma = value.indexOf(',');
            return comma > -1 ? value.substring(0, comma).trim() : value.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
