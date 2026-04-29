package com.vspicy.video.dto;

import java.util.List;

public record VideoPlaybackReadinessBatchResult(
        Boolean dryRun,
        Integer scannedCount,
        Integer problemCount,
        Integer successCount,
        Integer failedCount,
        List<VideoPlaybackReadinessView> readinessList,
        List<VideoPlaybackReadinessSyncResult> syncResults
) {
}
