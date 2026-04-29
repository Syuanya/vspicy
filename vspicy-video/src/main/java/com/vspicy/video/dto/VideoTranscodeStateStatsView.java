package com.vspicy.video.dto;

public record VideoTranscodeStateStatsView(
        Long totalCount,
        Long pendingCount,
        Long dispatchedCount,
        Long runningCount,
        Long successCount,
        Long failedCount,
        Long canceledCount,
        Long retryExhaustedCount
) {
}
