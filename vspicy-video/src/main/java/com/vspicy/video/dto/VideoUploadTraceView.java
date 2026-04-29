package com.vspicy.video.dto;

public record VideoUploadTraceView(
        Long id,
        String traceId,
        Long userId,
        Long videoId,
        Long recordId,
        String uploadTaskId,
        String bucket,
        String objectKey,
        String fileName,
        Long sizeMb,
        String status,
        String source,
        String remark,
        String createdAt,
        String updatedAt
) {
}
