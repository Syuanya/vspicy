package com.vspicy.admin.dto;

public record ExceptionLogCleanupView(
        Integer retentionDays,
        Boolean dryRun,
        Long matchedCount,
        Long deletedCount
) {
}
