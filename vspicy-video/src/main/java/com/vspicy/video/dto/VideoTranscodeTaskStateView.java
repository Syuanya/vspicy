package com.vspicy.video.dto;

public record VideoTranscodeTaskStateView(
        Long id,
        Long videoId,
        String sourceFilePath,
        String status,
        Integer retryCount,
        Integer maxRetryCount,
        String errorMessage,
        String lastDispatchMode,
        String lastDispatchError,
        String dispatchedAt,
        String startedAt,
        String finishedAt,
        String canceledAt,
        String createdAt,
        String updatedAt
) {
}
