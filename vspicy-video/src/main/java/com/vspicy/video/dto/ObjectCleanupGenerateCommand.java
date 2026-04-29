package com.vspicy.video.dto;

public record ObjectCleanupGenerateCommand(
        String prefix,
        Integer limit
) {
}
