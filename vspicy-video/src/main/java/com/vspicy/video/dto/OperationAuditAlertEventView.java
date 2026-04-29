package com.vspicy.video.dto;

import java.util.List;

public record OperationAuditAlertEventView(
        Long id,
        String dedupKey,
        String alertType,
        String alertLevel,
        String title,
        String message,
        String action,
        String targetType,
        String targetId,
        Long operatorId,
        String operatorName,
        String requestIp,
        Long count,
        List<Long> evidenceAuditIds,
        String link,
        String status,
        String firstSeenAt,
        String lastSeenAt,
        String ackedAt,
        String resolvedAt,
        String createdAt,
        String updatedAt
) {
}
