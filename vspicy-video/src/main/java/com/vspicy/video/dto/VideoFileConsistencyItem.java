package com.vspicy.video.dto;

public record VideoFileConsistencyItem(
        String issueType,
        String bucket,
        String objectKey,
        Long objectSize,
        Long videoFileId,
        Long videoId,
        Long traceId,
        Long recordId,
        String source,
        String message
) {
}
