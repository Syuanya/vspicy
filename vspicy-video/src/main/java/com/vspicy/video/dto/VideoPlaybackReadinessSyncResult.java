package com.vspicy.video.dto;

import java.util.List;

public record VideoPlaybackReadinessSyncResult(
        Long videoId,
        Boolean dryRun,
        Boolean success,
        Boolean hlsReady,
        String hlsManifestKey,
        String videoStatusBefore,
        String videoStatusAfter,
        List<String> columnsToUpdate,
        List<String> columnsUpdated,
        String message,
        String errorMessage
) {
}
