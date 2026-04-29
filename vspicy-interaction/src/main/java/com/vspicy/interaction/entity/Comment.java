package com.vspicy.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contentId;
    private String contentType;
    private Long parentId;
    private Long userId;
    private Long replyToUserId;
    private String content;
    private String status;
    private Long likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public Long getContentId() { return contentId; }
    public String getContentType() { return contentType; }
    public Long getParentId() { return parentId; }
    public Long getUserId() { return userId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public Long getLikeCount() { return likeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Integer getDeleted() { return deleted; }

    public void setId(Long id) { this.id = id; }
    public void setContentId(Long contentId) { this.contentId = contentId; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
    public void setContent(String content) { this.content = content; }
    public void setStatus(String status) { this.status = status; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
