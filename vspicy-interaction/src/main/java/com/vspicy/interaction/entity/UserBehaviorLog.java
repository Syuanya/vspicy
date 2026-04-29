package com.vspicy.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_behavior_log")
public class UserBehaviorLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long targetId;
    private String targetType;
    private String actionType;
    private Integer durationSeconds;
    private String extraJson;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTargetId() { return targetId; }
    public String getTargetType() { return targetType; }
    public String getActionType() { return actionType; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public String getExtraJson() { return extraJson; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
