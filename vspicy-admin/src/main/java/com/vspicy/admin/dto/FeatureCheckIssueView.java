package com.vspicy.admin.dto;

public record FeatureCheckIssueView(
        Long id,
        String runNo,
        String issueType,
        String severity,
        Long featureId,
        String featureCode,
        String featureName,
        String moduleName,
        String targetType,
        String targetValue,
        String issueMessage,
        String status,
        String handleRemark,
        Long handledBy,
        String handledAt,
        String createdAt,
        String updatedAt
) {
}
