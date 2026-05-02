package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record ExceptionLogView(
        Long id,
        String traceId,
        String serviceName,
        String environment,
        String severity,
        String status,
        String exceptionType,
        String exceptionMessage,
        String requestMethod,
        String requestUri,
        String requestParams,
        Long userId,
        String username,
        String clientIp,
        String userAgent,
        String stackTrace,
        Integer occurrenceCount,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        Long resolvedBy,
        LocalDateTime resolvedAt,
        String resolutionNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
