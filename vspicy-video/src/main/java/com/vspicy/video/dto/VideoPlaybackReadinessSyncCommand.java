package com.vspicy.video.dto;

public record VideoPlaybackReadinessSyncCommand(
        Boolean dryRun,
        String reason
) {
}
