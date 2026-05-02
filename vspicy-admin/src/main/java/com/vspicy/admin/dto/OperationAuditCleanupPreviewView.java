package com.vspicy.admin.dto;

public record OperationAuditCleanupPreviewView(
        int beforeDays,
        boolean onlyHandled,
        String beforeDate,
        long matchedCount
) {
}
