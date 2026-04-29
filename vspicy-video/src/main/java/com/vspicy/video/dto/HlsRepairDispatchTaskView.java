package com.vspicy.video.dto;

public record HlsRepairDispatchTaskView(
        Long taskId,
        String repairType,
        String status,
        String manifestObjectKey,
        Long videoId,
        Long recordId,
        Long traceId,
        Integer retryCount,
        Integer maxRetryCount,
        String dispatchTopic,
        String dispatchTag,
        String message
) {
}
