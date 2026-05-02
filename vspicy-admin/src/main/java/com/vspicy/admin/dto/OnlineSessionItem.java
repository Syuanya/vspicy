package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record OnlineSessionItem(
        Long id,
        Long userId,
        String username,
        String nickname,
        String tokenId,
        String ip,
        String device,
        String status,
        LocalDateTime loginAt,
        LocalDateTime lastActiveAt,
        LocalDateTime expireAt
) {
}
