package com.vspicy.content.dto;

public record ArticleSaveCommand(
        Long userId,
        String title,
        String summary,
        String coverUrl,
        String content
) {
}
