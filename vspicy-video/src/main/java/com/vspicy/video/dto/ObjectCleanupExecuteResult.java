package com.vspicy.video.dto;

import java.util.List;

public record ObjectCleanupExecuteResult(
        Boolean dryRun,
        Long selectedCount,
        Long deletedCount,
        Long skippedCount,
        Long failedCount,
        String message,
        List<ObjectCleanupRequestView> items
) {
}
