package com.vspicy.video.dto;

public record HlsRepairTaskView(
        Long id,
        String dedupKey,
        String repairType,
        String status,
        Integer priority,
        String bucket,
        String manifestObjectKey,
        String missingSegments,
        Integer missingSegmentCount,
        Long videoId,
        Long recordId,
        Long traceId,
        Long alertId,
        String source,
        Integer retryCount,
        Integer maxRetryCount,
        String lastError,
        String dispatchedAt,
        String startedAt,
        String finishedAt,
        String createdAt,
        String updatedAt
) {
}
