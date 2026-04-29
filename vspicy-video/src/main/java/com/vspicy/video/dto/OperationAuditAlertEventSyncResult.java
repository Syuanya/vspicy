package com.vspicy.video.dto;

public record OperationAuditAlertEventSyncResult(
        Integer hours,
        Integer limit,
        Long generatedCount,
        Long openCount,
        Long ackedCount,
        Long resolvedCount,
        String message
) {
}
