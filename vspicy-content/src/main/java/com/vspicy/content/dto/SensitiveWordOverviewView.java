package com.vspicy.content.dto;

import java.util.List;

public record SensitiveWordOverviewView(
        Long totalCount,
        Long enabledCount,
        Long disabledCount,
        Long highRiskCount,
        Long mediumRiskCount,
        Long lowRiskCount,
        List<SensitiveWordMetricItem> categoryStats,
        List<SensitiveWordMetricItem> riskStats
) {
}
