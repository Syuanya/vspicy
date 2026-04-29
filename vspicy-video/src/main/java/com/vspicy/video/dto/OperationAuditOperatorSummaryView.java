package com.vspicy.video.dto;

public record OperationAuditOperatorSummaryView(
        Long operatorId,
        String operatorName,
        Long count,
        Long rejectedCount,
        Long dangerCount,
        String riskLevel,
        String lastCreatedAt
) {
}
