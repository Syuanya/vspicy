package com.vspicy.admin.dto;

import java.util.List;

public record FeatureGovernanceOverviewView(
        Long totalFeatures,
        Long enabledFeatures,
        Long disabledFeatures,
        Long openIssues,
        Long blockerIssues,
        Long highIssues,
        Long warnIssues,
        Long resolvedIssues,
        String lastRunNo,
        String lastRunAt,
        List<FeatureMetricItem> typeDistribution,
        List<FeatureMetricItem> moduleDistribution,
        List<FeatureMetricItem> issueDistribution
) {
}
