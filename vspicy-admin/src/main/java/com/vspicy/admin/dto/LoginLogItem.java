package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record LoginLogItem(
        Long id,
        Long userId,
        String username,
        String nickname,
        String loginType,
        String ip,
        String location,
        String userAgent,
        String device,
        String status,
        String failReason,
        LocalDateTime createdAt
) {
}
