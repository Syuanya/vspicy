package com.vspicy.video.dto;

public record OperationAuditLogView(
        Long id,
        String action,
        String targetType,
        String targetId,
        Long operatorId,
        String operatorName,
        String description,
        String requestIp,
        String userAgent,
        String detailJson,
        String createdAt
) {
}
