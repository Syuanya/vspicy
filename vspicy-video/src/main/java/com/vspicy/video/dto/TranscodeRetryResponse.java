package com.vspicy.video.dto;

public record TranscodeRetryResponse(
        Long taskId,
        Long videoId,
        String status,
        Integer retryCount,
        String message
) {
}
