package com.vspicy.content.dto;

import com.vspicy.content.entity.Article;

public record ArticleDetailResponse(
        Article article,
        String content,
        Integer versionNo
) {
}
