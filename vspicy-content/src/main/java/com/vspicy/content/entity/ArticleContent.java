package com.vspicy.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("article_content")
public class ArticleContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String contentType;
    private String content;
    private Integer versionNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public String getContentType() { return contentType; }
    public String getContent() { return content; }
    public Integer getVersionNo() { return versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setContent(String content) { this.content = content; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
