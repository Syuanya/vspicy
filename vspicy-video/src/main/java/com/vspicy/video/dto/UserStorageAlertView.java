package com.vspicy.video.dto;

public record UserStorageAlertView(
        Long userId,
        String username,
        String planCode,
        Long totalUsedMb,
        Long totalLimitMb,
        Integer usagePercent,
        String level,
        String message
) {
}
