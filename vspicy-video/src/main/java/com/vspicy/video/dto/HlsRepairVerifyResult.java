package com.vspicy.video.dto;

import java.util.List;

public record HlsRepairVerifyResult(
        Integer limit,
        Boolean dryRun,
        Boolean markFailedOnError,
        Long selectedCount,
        Long okCount,
        Long stillBrokenCount,
        Long alertResolvedCount,
        Long failedCount,
        String message,
        List<HlsRepairVerifyTaskView> tasks
) {
}
