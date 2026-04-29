package com.vspicy.video.dto;

public record VideoStorageAlertView(
        Long id,
        String dedupKey,
        String alertCode,
        String alertLevel,
        String title,
        String content,
        String targetType,
        String targetId,
        String objectKey,
        Long userId,
        Long videoId,
        String source,
        String status,
        String firstSeenAt,
        String lastSeenAt,
        String ackedAt,
        String resolvedAt,
        String createdAt,
        String updatedAt
) {
}
