package com.vspicy.video.dto;

import java.util.List;

public record VideoStorageScanResult(
        String bucket,
        String prefix,
        Integer limit,
        Long dbObjectCount,
        Long minioObjectCount,
        Long dbMissingObjectCount,
        Long objectMissingDbCount,
        List<VideoStorageScanItem> items
) {
}
