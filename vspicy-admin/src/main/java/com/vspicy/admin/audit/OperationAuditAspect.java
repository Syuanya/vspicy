package com.vspicy.admin.audit;

import com.vspicy.admin.entity.OperationLog;
import com.vspicy.admin.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class OperationAuditAspect {
    private final OperationLogMapper operationLogMapper;

    public OperationAuditAspect(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            saveLog(auditLog, costMs, error);
        }
    }

    private void saveLog(AuditLog auditLog, long costMs, Throwable error) {
        try {
            HttpServletRequest request = currentRequest();

            OperationLog log = new OperationLog();
            log.setUserId(resolveUserId(request));
            log.setUsername(header(request, "X-Username"));
            log.setRoles(limit(header(request, "X-Roles"), 512));
            log.setOperationType(auditLog.type());
            log.setOperationTitle(auditLog.title());
            log.setRequestMethod(request == null ? null : request.getMethod());
            log.setRequestUri(request == null ? null : request.getRequestURI());
            log.setClientIp(resolveClientIp(request));
            log.setUserAgent(limit(header(request, "User-Agent"), 512));
            log.setStatus(error == null ? "SUCCESS" : "FAILED");
            log.setCostMs(costMs);
            log.setErrorMessage(error == null ? null : limit(error.getMessage(), 1024));

            operationLogMapper.insert(log);
        } catch (Exception logError) {
            System.err.println("操作审计日志写入失败: " + logError.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private Long resolveUserId(HttpServletRequest request) {
        String value = header(request, "X-User-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
