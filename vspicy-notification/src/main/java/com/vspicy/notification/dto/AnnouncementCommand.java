package com.vspicy.notification.dto;

public record AnnouncementCommand(
        String title,
        String content,
        String category,
        String priority,
        Boolean pinned,
        String publishStartAt,
        String publishEndAt,
        String remark
) {
}
