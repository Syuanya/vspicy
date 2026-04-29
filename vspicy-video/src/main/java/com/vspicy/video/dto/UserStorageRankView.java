package com.vspicy.video.dto;

public record UserStorageRankView(
        Long userId,
        String username,
        String planCode,
        Long totalUsedMb,
        Long totalLimitMb,
        Integer usagePercent,
        Long confirmedRecordCount,
        Long releasedRecordCount
) {
}
