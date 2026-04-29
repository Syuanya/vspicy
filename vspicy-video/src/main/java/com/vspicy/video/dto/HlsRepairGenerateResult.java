package com.vspicy.video.dto;

public record HlsRepairGenerateResult(
        Long scannedCount,
        Long createdCount,
        Long existingCount,
        Long pendingCount,
        Long failedCount,
        String message
) {
}
