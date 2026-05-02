package com.vspicy.admin.dto;

public record SupportTicketAssignCommand(
        Long assigneeId,
        String assigneeName,
        String remark
) {
}
