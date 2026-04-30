package com.vspicy.admin.dto;

public record SystemConfigCommand(
        String configKey,
        String configName,
        String configValue,
        String configType,
        String groupCode,
        String description,
        Boolean editable,
        Boolean encrypted,
        Integer status
) {
}
