package com.vspicy.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("audit_task")
public class AuditTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bizId;
    private String bizType;
    private Long userId;
    private String title;
    private String riskLevel;
    private String status;
    private String reason;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getBizId() { return bizId; }
    public String getBizType() { return bizType; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getRiskLevel() { return riskLevel; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getReviewerId() { return reviewerId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setTitle(String title) { this.title = title; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
