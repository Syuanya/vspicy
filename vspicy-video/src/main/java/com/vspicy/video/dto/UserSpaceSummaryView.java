package com.vspicy.video.dto;

public record UserSpaceSummaryView(
        Long userId,
        String username,
        String nickname,
        String planCode,
        Long dailyUsedMb,
        Long monthlyUsedMb,
        Long totalUsedMb,
        Long confirmedRecordCount,
        Long releasedRecordCount,
        Long totalRecordCount,
        Boolean consistent
) {
}
