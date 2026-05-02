package com.vspicy.admin.dto;

import java.util.List;

public record SupportTicketView(
        Long id,
        String ticketNo,
        String title,
        String content,
        String category,
        String priority,
        String status,
        Long submitterId,
        String submitterName,
        String contact,
        Long assigneeId,
        String assigneeName,
        Long resolvedBy,
        String resolvedAt,
        String closedAt,
        String lastReplyAt,
        String tags,
        String source,
        String remark,
        String createdAt,
        String updatedAt,
        List<SupportTicketReplyView> replies
) {
}
