package com.vspicy.video.dto;

public record VideoStorageAlertGenerateCommand(
        String prefix,
        Integer limit,
        Integer threshold,
        Integer hlsLimit
) {
}
