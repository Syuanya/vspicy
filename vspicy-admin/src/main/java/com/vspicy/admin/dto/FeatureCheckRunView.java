package com.vspicy.admin.dto;

public record FeatureCheckRunView(
        String runNo,
        Integer totalFeatures,
        Integer totalIssues,
        Integer openIssues,
        Integer duplicateRoutes,
        Integer duplicatePermissions,
        Integer duplicateApis,
        Integer missingRoutes,
        Integer missingPermissions,
        String runStatus,
        String createdAt
) {
}
