package com.vspicy.video.dto;

public record OperationAuditAlertEventCleanupCommand(
        Integer retentionDays,
        Integer limit,
        Boolean dryRun
) {
}
