package com.vspicy.video.dto;

import java.util.List;

public record VideoStorageOpsConsoleView(
        String bucket,
        String prefix,
        Integer limit,
        Integer threshold,
        VideoStorageDashboardView dashboard,

        Long alertOpenCount,
        Long alertAckedCount,
        Long alertResolvedCount,
        Long alertCriticalOpenCount,
        Long alertHighOpenCount,
        Long alertWarnOpenCount,

        Long repairPendingCount,
        Long repairDispatchedCount,
        Long repairRunningCount,
        Long repairSuccessCount,
        Long repairFailedCount,
        Long repairCanceledCount,

        Long cleanupPendingCount,
        Long cleanupApprovedCount,
        Long cleanupRejectedCount,
        Long cleanupExecutedCount,
        Long cleanupFailedCount,

        List<StorageOpsLinkView> links
) {
}
