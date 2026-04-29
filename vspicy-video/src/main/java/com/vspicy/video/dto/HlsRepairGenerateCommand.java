package com.vspicy.video.dto;

public record HlsRepairGenerateCommand(
        String prefix,
        Integer limit
) {
}
