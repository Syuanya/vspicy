package com.vspicy.video.dto;

public record OperationAuditAlertEventCleanupResult(
        Integer retentionDays,
        Integer limit,
        Boolean dryRun,
        Long candidateCount,
        Long deletedCount,
        String message
) {
}
