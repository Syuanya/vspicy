package com.vspicy.video.dto;

public record ObjectCleanupRequestView(
        Long id,
        String dedupKey,
        String bucket,
        String objectKey,
        Long objectSize,
        String issueType,
        String source,
        String status,
        String reason,
        Long approveUserId,
        Long rejectUserId,
        Long executeUserId,
        String errorMessage,
        String approvedAt,
        String rejectedAt,
        String executedAt,
        String createdAt,
        String updatedAt
) {
}
