package com.vspicy.video.dto;

public record OperationAuditCommand(
        String action,
        String targetType,
        String targetId,
        Long operatorId,
        String operatorName,
        String description,
        String detailJson
) {
}
