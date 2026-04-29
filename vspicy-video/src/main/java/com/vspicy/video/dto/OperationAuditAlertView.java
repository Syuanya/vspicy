package com.vspicy.video.dto;

import java.util.List;

public record OperationAuditAlertView(
        String alertLevel,
        String alertType,
        String message,
        String action,
        String targetType,
        String targetId,
        Long operatorId,
        String operatorName,
        String requestIp,
        Long count,
        String firstTime,
        String lastTime,
        List<Long> evidenceAuditIds,
        String link
) {
}
