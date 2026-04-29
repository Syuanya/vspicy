package com.vspicy.dashboard.dto;

import java.util.List;

public record DashboardOverviewResponse(
        List<MetricCard> metrics
) {
}
