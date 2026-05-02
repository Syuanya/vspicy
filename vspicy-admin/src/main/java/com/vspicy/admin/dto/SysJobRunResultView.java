package com.vspicy.admin.dto;

public record SysJobRunResultView(
        Long jobId,
        Long logId,
        String status,
        String message,
        Long costMs
) {
}
