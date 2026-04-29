package com.vspicy.video.dto;

public record HlsRepairVerifyCommand(
        Integer limit,
        Boolean dryRun,
        Boolean markFailedOnError
) {
}
