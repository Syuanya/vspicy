package com.vspicy.admin.dto;

public record FeatureRegistryCommand(
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
        String description
) {
}
