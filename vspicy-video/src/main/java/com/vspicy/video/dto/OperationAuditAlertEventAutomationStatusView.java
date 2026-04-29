package com.vspicy.video.dto;

public record OperationAuditAlertEventAutomationStatusView(
        String generatedAt,
        Boolean enabled,
        Boolean running,
        Long fixedDelayMs,
        Long initialDelayMs,
        Integer hours,
        Integer limit,
        String lastRunAt,
        String lastSuccessAt,
        String lastErrorAt,
        String lastErrorMessage,
        Long lastGeneratedCount,
        Long lastOpenCount,
        Long lastAckedCount,
        Long lastResolvedCount,
        String lastMessage
) {
}
