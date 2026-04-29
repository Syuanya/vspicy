package com.vspicy.video.dto;

public record VideoUploadQuotaReleaseCommand(
        Long recordId,
        Long videoId,
        String reason
) {
}
