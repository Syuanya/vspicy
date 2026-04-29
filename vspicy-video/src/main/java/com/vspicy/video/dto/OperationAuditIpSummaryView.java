package com.vspicy.video.dto;

public record OperationAuditIpSummaryView(
        String requestIp,
        Long count,
        Long rejectedCount,
        Long dangerCount,
        String riskLevel,
        String lastCreatedAt
) {
}
