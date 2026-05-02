package com.vspicy.admin.dto;

import java.util.List;

public record SystemReleaseOverviewView(
        Long totalReleases,
        Long plannedReleases,
        Long releasingReleases,
        Long successReleases,
        Long failedReleases,
        Long rolledBackReleases,
        Long highRiskReleases,
        Long todayReleases,
        List<SystemReleaseMetricItem> statusDistribution,
        List<SystemReleaseMetricItem> environmentDistribution,
        List<SystemReleaseMetricItem> riskDistribution
) {
}
