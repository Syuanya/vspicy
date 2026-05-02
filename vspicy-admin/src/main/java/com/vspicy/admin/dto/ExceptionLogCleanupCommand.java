package com.vspicy.admin.dto;

public record ExceptionLogCleanupCommand(
        Integer retentionDays,
        Boolean dryRun
) {
}
