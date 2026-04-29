package com.vspicy.video.dto;

public record VideoStorageScanItem(
        String issueType,
        String bucket,
        String objectKey,
        Long size,
        Long recordId,
        Long traceId,
        Long videoId,
        String source,
        String message
) {
}
