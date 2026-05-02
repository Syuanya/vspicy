package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_exception_log")
public class SystemExceptionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String serviceName;
    private String environment;
    private String severity;
    private String status;
    private String exceptionType;
    private String exceptionMessage;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private Long userId;
    private String username;
    private String clientIp;
    private String userAgent;
    private String stackTrace;
    private Integer occurrenceCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getTraceId() { return traceId; }
    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getExceptionType() { return exceptionType; }
    public String getExceptionMessage() { return exceptionMessage; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestUri() { return requestUri; }
    public String getRequestParams() { return requestParams; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getStackTrace() { return stackTrace; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setStatus(String status) { this.status = status; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
