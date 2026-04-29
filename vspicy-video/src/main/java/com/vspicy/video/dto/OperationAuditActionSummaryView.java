package com.vspicy.video.dto;

public record OperationAuditActionSummaryView(
        String action,
        Long count,
        Long rejectedCount,
        Long dangerCount,
        String riskLevel,
        String lastCreatedAt
) {
}
