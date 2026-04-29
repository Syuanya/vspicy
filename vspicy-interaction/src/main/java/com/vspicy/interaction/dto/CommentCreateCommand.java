package com.vspicy.interaction.dto;

public record CommentCreateCommand(
        Long contentId,
        String contentType,
        Long parentId,
        Long userId,
        Long replyToUserId,
        String content
) {
}
