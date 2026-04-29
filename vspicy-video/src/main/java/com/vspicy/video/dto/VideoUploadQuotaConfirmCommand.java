package com.vspicy.video.dto;

public record VideoUploadQuotaConfirmCommand(
        Long userId,
        Long videoId,
        String fileName,
        Long sizeMb
) {
}
