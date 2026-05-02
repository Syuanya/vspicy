package com.vspicy.admin.dto;

import java.util.List;

public record OperationAuditOverviewView(
        long totalCount,
        long successCount,
        long failedCount,
        long highRiskCount,
        long todayCount,
        long openFailedCount,
        List<OperationAuditMetricItem> serviceDistribution,
        List<OperationAuditMetricItem> actionDistribution,
        List<OperationAuditMetricItem> riskDistribution,
        List<OperationAuditMetricItem> statusDistribution
) {
}
