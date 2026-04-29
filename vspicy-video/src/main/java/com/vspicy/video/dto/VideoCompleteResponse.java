package com.vspicy.video.dto;

public record VideoCompleteResponse(
        Long taskId,
        Long videoId,
        String status,
        String originPath,
        String hlsObjectKey,
        String hlsUrl
) {
}
