package com.vspicy.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("content_moderation_record")
public class ContentModerationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bizId;
    private String bizType;
    private Long userId;
    private String checkType;
    private String result;
    private String riskLevel;
    private String matchedWords;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getBizId() { return bizId; }
    public String getBizType() { return bizType; }
    public Long getUserId() { return userId; }
    public String getCheckType() { return checkType; }
    public String getResult() { return result; }
    public String getRiskLevel() { return riskLevel; }
    public String getMatchedWords() { return matchedWords; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public void setResult(String result) { this.result = result; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setMatchedWords(String matchedWords) { this.matchedWords = matchedWords; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
