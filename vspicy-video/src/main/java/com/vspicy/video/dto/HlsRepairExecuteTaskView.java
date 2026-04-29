package com.vspicy.video.dto;

public record HlsRepairExecuteTaskView(
        Long taskId,
        String repairType,
        String status,
        String manifestObjectKey,
        Long videoId,
        Long recordId,
        Long traceId,
        String executorBean,
        String executorMethod,
        String message
) {
}
