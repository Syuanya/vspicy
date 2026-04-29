package com.vspicy.video.dto;

import java.util.List;

public record HlsRepairDispatchResult(
        Integer limit,
        Boolean dryRun,
        String topic,
        String tag,
        Boolean rocketMqAvailable,
        Long selectedCount,
        Long dispatchedCount,
        Long skippedCount,
        Long failedCount,
        String message,
        List<HlsRepairDispatchTaskView> tasks
) {
}
