package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String roles;
    private String operationType;
    private String operationTitle;
    private String requestMethod;
    private String requestUri;
    private String clientIp;
    private String userAgent;
    private String status;
    private Long costMs;
    private String errorMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRoles() { return roles; }
    public String getOperationType() { return operationType; }
    public String getOperationTitle() { return operationTitle; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestUri() { return requestUri; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getStatus() { return status; }
    public Long getCostMs() { return costMs; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setRoles(String roles) { this.roles = roles; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public void setOperationTitle(String operationTitle) { this.operationTitle = operationTitle; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setStatus(String status) { this.status = status; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
