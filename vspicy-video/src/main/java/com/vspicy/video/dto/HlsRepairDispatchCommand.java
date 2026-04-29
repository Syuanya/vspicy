package com.vspicy.video.dto;

public record HlsRepairDispatchCommand(
        Integer limit,
        Boolean dryRun
) {
}
