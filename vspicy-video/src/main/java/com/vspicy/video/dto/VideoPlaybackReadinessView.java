package com.vspicy.video.dto;

import java.util.List;

public record VideoPlaybackReadinessView(
        Long videoId,
        Boolean videoExists,
        String videoStatus,
        Boolean hlsReady,
        String hlsManifestKey,
        Boolean statusPublished,
        Boolean playbackUrlPresent,
        String playbackUrl,
        Boolean playable,
        String message,
        String suggestedAction,
        List<String> detectedPlaybackColumns
) {
}
