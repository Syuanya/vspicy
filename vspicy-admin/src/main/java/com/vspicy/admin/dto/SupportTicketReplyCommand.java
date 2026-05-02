package com.vspicy.admin.dto;

public record SupportTicketReplyCommand(
        String content,
        String replyType,
        Boolean visibleToUser
) {
}
