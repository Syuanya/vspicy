package com.vspicy.content.controller;

import com.vspicy.common.core.Result;
import com.vspicy.content.dto.ArticleDetailResponse;
import com.vspicy.content.dto.ArticleSaveCommand;
import com.vspicy.content.entity.Article;
import com.vspicy.content.service.ArticleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping("/drafts")
    public Result<ArticleDetailResponse> createDraft(@RequestBody ArticleSaveCommand command) {
        return Result.ok(articleService.createDraft(command));
    }

    @PutMapping("/{articleId}")
    public Result<ArticleDetailResponse> update(
            @PathVariable("articleId") Long articleId,
            @RequestBody ArticleSaveCommand command
    ) {
        return Result.ok(articleService.update(articleId, command));
    }

    @PostMapping("/{articleId}/submit")
    public Result<ArticleDetailResponse> submit(@PathVariable("articleId") Long articleId) {
        return Result.ok(articleService.submit(articleId));
    }

    @GetMapping("/{articleId}")
    public Result<ArticleDetailResponse> detail(@PathVariable("articleId") Long articleId) {
        return Result.ok(articleService.detail(articleId));
    }

    @GetMapping
    public Result<List<Article>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    ) {
        return Result.ok(articleService.list(status, userId, limit));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-content article ok");
    }
}
