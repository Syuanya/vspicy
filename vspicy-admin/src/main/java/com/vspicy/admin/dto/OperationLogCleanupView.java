package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record OperationLogCleanupView(
        Integer retentionDays,
        Boolean dryRun,
        LocalDateTime beforeTime,
        Long matchedCount,
        Long deletedCount
) {
}
