package com.vspicy.admin.dto;

import java.util.List;

public record OperationLogOverviewView(
        Integer days,
        Long totalCount,
        Long successCount,
        Long failedCount,
        Long slowCount,
        Long uniqueUserCount,
        Long avgCostMs,
        Double successRate,
        Double failedRate,
        List<OperationLogMetricItem> statusDistribution,
        List<OperationLogMetricItem> operationTypeDistribution,
        List<OperationLogMetricItem> topUsers,
        List<OperationLogDailyItem> dailyTrend
) {
}
