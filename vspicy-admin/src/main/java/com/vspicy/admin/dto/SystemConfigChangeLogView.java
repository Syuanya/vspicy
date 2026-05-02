package com.vspicy.admin.dto;

public record SystemConfigChangeLogView(
        Long id,
        Long configId,
        String configKey,
        String beforeValue,
        String afterValue,
        String changeType,
        String changeReason,
        Long operatorId,
        String operatorName,
        String operatorIp,
        String createdAt
) {
}
