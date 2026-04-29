package com.vspicy.video.dto;

import java.util.List;

public record HlsIntegrityItem(
        String status,
        String bucket,
        String manifestObjectKey,
        Long videoId,
        Long recordId,
        Long traceId,
        Integer segmentCount,
        Integer missingSegmentCount,
        List<String> missingSegments,
        String source,
        String message
) {
}
