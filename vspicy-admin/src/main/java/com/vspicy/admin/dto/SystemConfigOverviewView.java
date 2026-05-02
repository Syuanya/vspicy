package com.vspicy.admin.dto;

import java.util.List;

public record SystemConfigOverviewView(
        long total,
        long enabled,
        long disabled,
        long editable,
        long sensitive,
        long changedToday,
        List<SystemConfigMetricItem> categoryDistribution,
        List<SystemConfigMetricItem> typeDistribution,
        List<SystemConfigMetricItem> statusDistribution
) {
}
