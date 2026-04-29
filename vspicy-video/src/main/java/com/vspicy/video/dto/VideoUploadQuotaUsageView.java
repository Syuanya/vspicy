package com.vspicy.video.dto;

public record VideoUploadQuotaUsageView(
        Long userId,
        String planCode,
        Long maxFileMb,
        Long dailyLimitMb,
        Long dailyUsedMb,
        Long dailyRemainingMb,
        Long monthlyLimitMb,
        Long monthlyUsedMb,
        Long monthlyRemainingMb,
        Long totalLimitMb,
        Long totalUsedMb,
        Long totalRemainingMb
) {
}
