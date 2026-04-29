package com.vspicy.video.dto;

public record VideoPlaybackReadinessBatchCommand(
        Boolean dryRun,
        Integer limit,
        Boolean onlyProblem,
        String reason
) {
}
