package com.vspicy.video.dto;

public record HlsRepairExecuteCommand(
        Integer limit,
        Boolean dryRun,
        Boolean allowPending
) {
}
