package com.vspicy.admin.dto;

public record FeatureRegistryView(
        Long id,
        String featureCode,
        String featureName,
        String featureType,
        String moduleName,
        String serviceName,
        String routePath,
        String apiPath,
        String apiMethod,
        String permissionCode,
        String menuTitle,
        String menuGroup,
        String owner,
        String riskLevel,
        String status,
        String sourceType,
        String description,
        String createdAt,
        String updatedAt
) {
}
