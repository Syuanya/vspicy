package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record SysJobLogView(
        Long id,
        Long jobId,
        String jobCode,
        String jobName,
        String jobGroup,
        String triggerType,
        String runStatus,
        String runMessage,
        String errorMessage,
        Long costMs,
        String operatorId,
        String operatorName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
