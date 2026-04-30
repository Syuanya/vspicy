package com.vspicy.admin.dto;

public record OperationLogCleanupCommand(
        Integer retentionDays,
        Boolean dryRun
) {
}
