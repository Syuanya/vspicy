package com.vspicy.video.dto;

public record OperationAuditAlertEventSyncCommand(
        Integer hours,
        Integer limit
) {
}
