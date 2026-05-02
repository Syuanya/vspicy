package com.vspicy.notification.dto;

public record AnnouncementView(
        Long id,
        String title,
        String content,
        String category,
        String priority,
        String status,
        Boolean pinned,
        String publishStartAt,
        String publishEndAt,
        Long publishedMessageId,
        Long createdBy,
        Long updatedBy,
        Long publishedBy,
        String publishedAt,
        String offlineAt,
        String remark,
        String createdAt,
        String updatedAt
) {
}
