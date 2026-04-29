package com.vspicy.video.dto;

public record VideoUploadQuotaCheckResponse(
        Long userId,
        String planCode,
        Long fileSizeMb,
        Boolean allowed,
        String reason,
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
