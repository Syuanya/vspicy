package com.vspicy.video.dto;

import java.util.List;

public record HlsRepairExecuteResult(
        Integer limit,
        Boolean dryRun,
        Boolean allowPending,
        Long selectedCount,
        Long executedCount,
        Long skippedCount,
        Long failedCount,
        String message,
        List<HlsRepairExecuteTaskView> tasks
) {
}
