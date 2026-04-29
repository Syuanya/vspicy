package com.vspicy.video.dto;

public record OperationAuditStatsView(
        Long totalCount,
        Long todayCount,
        Long transcodeActionCount,
        Long playbackActionCount,
        Long cleanupActionCount,
        Long hlsRepairActionCount,
        Long storageActionCount,
        Long rejectedActionCount,
        Long dangerActionCount,
        Long successActionCount
) {
}
