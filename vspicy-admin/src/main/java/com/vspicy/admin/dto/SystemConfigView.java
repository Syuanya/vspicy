package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record SystemConfigView(
        Long id,
        String configKey,
        String configName,
        String configValue,
        String configType,
        String groupCode,
        String description,
        Boolean editable,
        Boolean encrypted,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
