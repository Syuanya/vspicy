package com.vspicy.video.dto;

public record VideoTranscodeProgressView(
        Long videoId,
        Long transcodeTaskId,
        String status,
        Integer retryCount,
        Integer maxRetryCount,
        String sourceFilePath,
        Boolean hlsReady,
        String hlsManifestKey,
        Boolean playable,
        String displayStatus,
        String displayMessage,
        String suggestedAction,
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
