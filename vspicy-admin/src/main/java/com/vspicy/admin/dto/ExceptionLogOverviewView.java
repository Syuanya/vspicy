package com.vspicy.admin.dto;

import java.util.List;

public record ExceptionLogOverviewView(
        Integer days,
        Long totalCount,
        Long newCount,
        Long processingCount,
        Long resolvedCount,
        Long ignoredCount,
        Long unresolvedCount,
        Long criticalCount,
        Long affectedServiceCount,
        Long todayNewCount,
        List<ExceptionLogMetricItem> severityDistribution,
        List<ExceptionLogMetricItem> statusDistribution,
        List<ExceptionLogMetricItem> serviceDistribution,
        List<ExceptionLogMetricItem> exceptionTypeDistribution,
        List<ExceptionLogDailyItem> dailyTrend
) {
}
