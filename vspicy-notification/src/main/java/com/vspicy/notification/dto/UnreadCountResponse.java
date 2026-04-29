package com.vspicy.notification.dto;

public record UnreadCountResponse(
        Long userId,
        long unreadCount
) {
}
