package com.vspicy.video.dto;

public record VideoUploadQuotaReconcilePreview(
        Long userId,
        Long currentDailyMb,
        Long recordDailyMb,
        Long currentMonthlyMb,
        Long recordMonthlyMb,
        Long currentTotalMb,
        Long recordTotalMb,
        Boolean consistent
) {
}
