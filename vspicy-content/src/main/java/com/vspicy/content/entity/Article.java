package com.vspicy.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("article")
public class Article {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String summary;
    private String coverUrl;
    private String status;
    private Long viewCount;
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getCoverUrl() { return coverUrl; }
    public String getStatus() { return status; }
    public Long getViewCount() { return viewCount; }
    public Long getLikeCount() { return likeCount; }
    public Long getFavoriteCount() { return favoriteCount; }
    public Long getCommentCount() { return commentCount; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Integer getDeleted() { return deleted; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setTitle(String title) { this.title = title; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setStatus(String status) { this.status = status; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public void setFavoriteCount(Long favoriteCount) { this.favoriteCount = favoriteCount; }
    public void setCommentCount(Long commentCount) { this.commentCount = commentCount; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
