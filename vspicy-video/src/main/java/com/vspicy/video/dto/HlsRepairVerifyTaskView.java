package com.vspicy.video.dto;

public record HlsRepairVerifyTaskView(
        Long taskId,
        String repairType,
        String beforeStatus,
        String afterStatus,
        String manifestObjectKey,
        Long videoId,
        Long recordId,
        Long traceId,
        Long alertId,
        String verifyStatus,
        String verifyMessage,
        Boolean alertResolved,
        String message
) {
}
