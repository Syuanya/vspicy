package com.vspicy.admin.dto;

public record OperationAuditCleanupCommand(Integer beforeDays, Boolean onlyHandled) {
}
