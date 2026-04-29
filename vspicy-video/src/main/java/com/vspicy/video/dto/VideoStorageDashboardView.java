package com.vspicy.video.dto;

import java.util.List;

public record VideoStorageDashboardView(
        String bucket,
        String prefix,
        Integer limit,
        Integer threshold,
        Long minioObjectCount,
        Long minioTotalBytes,
        Long minioTotalMb,
        Long dbObjectCount,
        Long dbMissingObjectCount,
        Long objectMissingDbCount,
        Long userCount,
        Long totalUsedMb,
        Long confirmedRecordCount,
        Long releasedRecordCount,
        List<UserStorageRankView> topUsers,
        List<UserStorageAlertView> alerts
) {
}
