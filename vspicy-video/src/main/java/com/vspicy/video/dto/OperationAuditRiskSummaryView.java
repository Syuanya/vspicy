package com.vspicy.video.dto;

import java.util.List;

public record OperationAuditRiskSummaryView(
        String generatedAt,
        Integer hours,
        Long totalCount,
        Long rejectedCount,
        Long dangerCount,
        String riskLevel,
        List<OperationAuditActionSummaryView> actionStats,
        List<OperationAuditOperatorSummaryView> operatorStats,
        List<OperationAuditIpSummaryView> ipStats,
        List<OperationAuditLogView> recentRejected
) {
}
