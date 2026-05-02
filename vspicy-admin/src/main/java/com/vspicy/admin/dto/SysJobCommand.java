package com.vspicy.admin.dto;

public record SysJobCommand(
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
        Boolean editable
) {
}
