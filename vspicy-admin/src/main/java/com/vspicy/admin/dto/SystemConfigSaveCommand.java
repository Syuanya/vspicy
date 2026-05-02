package com.vspicy.admin.dto;

public record SystemConfigSaveCommand(
        String configKey,
        String configName,
        String configValue,
        String defaultValue,
        String category,
        String valueType,
        Boolean editable,
        Boolean sensitive,
        Boolean required,
        String validationRule,
        String description,
        String status,
        String changeReason
) {
}
