package com.vspicy.admin.dto;

public record SupportTicketCommand(
        String title,
        String content,
        String category,
        String priority,
        Long submitterId,
        String submitterName,
        String contact,
        Long assigneeId,
        String assigneeName,
        String tags,
        String source,
        String remark
) {
}
