package com.vspicy.video.dto;

public record OperationAuditAlertEventSummaryView(
        String generatedAt,
        Long openCount,
        Long ackedCount,
        Long resolvedCount,
        Long criticalOpenCount,
        Long dangerOpenCount,
        Long warningOpenCount,
        Long totalCount,
        String highestLevel
) {
}
