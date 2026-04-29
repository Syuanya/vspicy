package com.vspicy.video.dto;

import java.util.List;

public record VideoStorageCleanupResult(
        String bucket,
        String prefix,
        Boolean dryRun,
        Long candidateCount,
        Long deletedCount,
        List<VideoStorageScanItem> candidates,
        String message
) {
}
