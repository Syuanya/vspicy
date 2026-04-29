package com.vspicy.video.dto;

public record VideoUploadQuotaReconcileResult(
        Long userId,
        Boolean fullReconcile,
        Long affectedUserCount,
        Long confirmedRecordCount,
        Long dailyRows,
        Long monthlyRows,
        Long totalRows,
        String message,
        String executedAt
) {
}
