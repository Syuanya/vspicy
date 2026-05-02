package com.vspicy.notification.dto;

public record AnnouncementPublishCommand(
        String publishStartAt,
        String publishEndAt,
        Boolean pinned
) {
}
