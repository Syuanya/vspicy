package com.vspicy.admin.dto;

public record SystemConfigView(
        Long id,
        String configKey,
        String configName,
        String configValue,
        String rawConfigValue,
        String defaultValue,
        String category,
        String valueType,
        Boolean editable,
        Boolean sensitive,
        Boolean required,
        String validationRule,
        String description,
        String status,
        Integer version,
        Long updatedBy,
        String createdAt,
        String updatedAt
) {
}
