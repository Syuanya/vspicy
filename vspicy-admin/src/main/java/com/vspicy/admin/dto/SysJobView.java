package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record SysJobView(
        Long id,
        String jobCode,
        String jobName,
        String jobGroup,
        String jobType,
        String cronExpression,
        String invokeTarget,
        String jobParams,
        String description,
        Integer status,
        Boolean allowConcurrent,
        Integer misfirePolicy,
        Integer runCount,
        Integer failCount,
        LocalDateTime lastRunAt,
        LocalDateTime nextRunAt,
        String lastRunStatus,
        String lastError,
        Boolean editable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
