package com.vspicy.admin.dto;

import java.util.List;

public record SysJobOverviewView(
        long totalJobs,
        long enabledJobs,
        long disabledJobs,
        long totalRuns,
        long failedRuns,
        long todayRuns,
        long todayFailedRuns,
        double successRate,
        List<SysJobMetricItem> groupStats,
        List<SysJobMetricItem> statusStats,
        List<SysJobMetricItem> recentRunStats
) {
}
