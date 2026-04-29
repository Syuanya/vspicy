package com.vspicy.video.dto;

public record ObjectCleanupGenerateResult(
        String prefix,
        Integer limit,
        Long scannedIssueCount,
        Long createdCount,
        Long existingCount,
        Long pendingCount,
        String message
) {
}
