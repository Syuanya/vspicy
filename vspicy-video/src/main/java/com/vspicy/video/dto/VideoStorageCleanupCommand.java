package com.vspicy.video.dto;

public record VideoStorageCleanupCommand(
        String prefix,
        Integer limit,
        Boolean dryRun
) {
}
