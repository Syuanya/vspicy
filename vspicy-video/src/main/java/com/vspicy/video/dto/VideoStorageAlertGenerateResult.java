package com.vspicy.video.dto;

public record VideoStorageAlertGenerateResult(
        String prefix,
        Integer limit,
        Integer threshold,
        Integer hlsLimit,
        Long generatedCount,
        Long openCount,
        Long ackedCount,
        Long resolvedCount,
        String message
) {
}
