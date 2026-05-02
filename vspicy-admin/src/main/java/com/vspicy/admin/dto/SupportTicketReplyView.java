package com.vspicy.admin.dto;

public record SupportTicketReplyView(
        Long id,
        Long ticketId,
        String content,
        String replyType,
        Boolean visibleToUser,
        Long operatorId,
        String operatorName,
        String createdAt
) {
}
