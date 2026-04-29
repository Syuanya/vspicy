package com.vspicy.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.content.dto.ArticleDetailResponse;
import com.vspicy.content.dto.ArticleSaveCommand;
import com.vspicy.content.dto.ModerationResult;
import com.vspicy.content.entity.Article;
import com.vspicy.content.entity.ArticleContent;
import com.vspicy.content.entity.AuditTask;
import com.vspicy.content.mapper.ArticleContentMapper;
import com.vspicy.content.mapper.ArticleMapper;
import com.vspicy.content.mapper.AuditTaskMapper;
import com.vspicy.content.security.HtmlSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ArticleService {
    private final ArticleMapper articleMapper;
    private final ArticleContentMapper articleContentMapper;
    private final AuditTaskMapper auditTaskMapper;
    private final HtmlSanitizer htmlSanitizer;
    private final ContentModerationService moderationService;

    public ArticleService(
            ArticleMapper articleMapper,
            ArticleContentMapper articleContentMapper,
            AuditTaskMapper auditTaskMapper,
            HtmlSanitizer htmlSanitizer,
            ContentModerationService moderationService
    ) {
        this.articleMapper = articleMapper;
        this.articleContentMapper = articleContentMapper;
        this.auditTaskMapper = auditTaskMapper;
        this.htmlSanitizer = htmlSanitizer;
        this.moderationService = moderationService;
    }

    @Transactional
    public ArticleDetailResponse createDraft(ArticleSaveCommand command) {
        validate(command);

        Article article = new Article();
        article.setUserId(command.userId() == null ? 1L : command.userId());
        article.setTitle(command.title());
        article.setSummary(command.summary());
        article.setCoverUrl(command.coverUrl());
        article.setStatus("DRAFT");
        article.setDeleted(0);
        articleMapper.insert(article);

        ArticleContent content = new ArticleContent();
        content.setArticleId(article.getId());
        content.setContentType("RICH_TEXT");
        content.setContent(htmlSanitizer.sanitize(command.content()));
        content.setVersionNo(1);
        articleContentMapper.insert(content);

        return detail(article.getId());
    }

    @Transactional
    public ArticleDetailResponse update(Long articleId, ArticleSaveCommand command) {
        validate(command);

        Article article = requireArticle(articleId);
        if ("AUDITING".equals(article.getStatus())) {
            throw new BizException("审核中的文章不能修改");
        }

        article.setTitle(command.title());
        article.setSummary(command.summary());
        article.setCoverUrl(command.coverUrl());
        if ("REJECTED".equals(article.getStatus())) {
            article.setStatus("DRAFT");
        }
        articleMapper.updateById(article);

        Integer nextVersion = nextVersion(articleId);

        ArticleContent content = new ArticleContent();
        content.setArticleId(articleId);
        content.setContentType("RICH_TEXT");
        content.setContent(htmlSanitizer.sanitize(command.content()));
        content.setVersionNo(nextVersion);
        articleContentMapper.insert(content);

        return detail(articleId);
    }

    @Transactional
    public ArticleDetailResponse submit(Long articleId) {
        Article article = requireArticle(articleId);
        if ("AUDITING".equals(article.getStatus())) {
            return detail(articleId);
        }
        if ("PUBLISHED".equals(article.getStatus())) {
            return detail(articleId);
        }

        ArticleContent latest = latestContent(articleId);
        if (latest == null || latest.getContent() == null || htmlSanitizer.plainText(latest.getContent()).isBlank()) {
            throw new BizException("文章内容不能为空");
        }

        ModerationResult moderation = moderationService.checkArticle(article, latest.getContent());

        article.setStatus("AUDITING");
        articleMapper.updateById(article);

        AuditTask existed = auditTaskMapper.selectOne(new LambdaQueryWrapper<AuditTask>()
                .eq(AuditTask::getBizId, articleId)
                .eq(AuditTask::getBizType, "ARTICLE")
                .eq(AuditTask::getStatus, "PENDING")
                .last("LIMIT 1"));

        if (existed == null) {
            AuditTask task = new AuditTask();
            task.setBizId(articleId);
            task.setBizType("ARTICLE");
            task.setUserId(article.getUserId());
            task.setTitle(article.getTitle());
            task.setRiskLevel(moderation.riskLevel());
            task.setStatus("PENDING");
            task.setReason(moderation.reason());
            auditTaskMapper.insert(task);
        } else {
            existed.setRiskLevel(moderation.riskLevel());
            existed.setReason(moderation.reason());
            auditTaskMapper.updateById(existed);
        }

        return detail(articleId);
    }

    public ArticleDetailResponse detail(Long articleId) {
        Article article = requireArticle(articleId);
        ArticleContent content = latestContent(articleId);
        return new ArticleDetailResponse(
                article,
                content == null ? "" : content.getContent(),
                content == null ? 0 : content.getVersionNo()
        );
    }

    public List<Article> list(String status, Long userId, Integer limit) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getDeleted, 0)
                .orderByDesc(Article::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(Article::getStatus, status);
        }
        if (userId != null) {
            wrapper.eq(Article::getUserId, userId);
        }

        int safeLimit = limit == null || limit <= 0 || limit > 200 ? 50 : limit;
        wrapper.last("LIMIT " + safeLimit);
        return articleMapper.selectList(wrapper);
    }

    private void validate(ArticleSaveCommand command) {
        if (command == null) {
            throw new BizException("请求体不能为空");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new BizException("标题不能为空");
        }
        if (command.title().length() > 160) {
            throw new BizException("标题不能超过160个字符");
        }
    }

    private Article requireArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || (article.getDeleted() != null && article.getDeleted() == 1)) {
            throw new BizException(404, "文章不存在");
        }
        return article;
    }

    private ArticleContent latestContent(Long articleId) {
        return articleContentMapper.selectOne(new LambdaQueryWrapper<ArticleContent>()
                .eq(ArticleContent::getArticleId, articleId)
                .orderByDesc(ArticleContent::getVersionNo)
                .last("LIMIT 1"));
    }

    private Integer nextVersion(Long articleId) {
        ArticleContent latest = latestContent(articleId);
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }
}
