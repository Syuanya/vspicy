package com.vspicy.video.dto;

public record VideoUploadRecordView(
        Long id,
        Long userId,
        Long videoId,
        String fileName,
        Long sizeMb,
        String status,
        String createdAt,
        String releasedAt,
        String releaseReason
) {
}
