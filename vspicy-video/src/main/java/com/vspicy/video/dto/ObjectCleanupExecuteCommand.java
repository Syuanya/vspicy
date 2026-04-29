package com.vspicy.video.dto;

public record ObjectCleanupExecuteCommand(
        Integer limit,
        Boolean dryRun
) {
}
