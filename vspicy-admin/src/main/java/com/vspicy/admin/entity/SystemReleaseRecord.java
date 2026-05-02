package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_release_record")
public class SystemReleaseRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String releaseNo;
    private String versionName;
    private String environment;
    private String status;
    private String riskLevel;
    private String title;
    private String description;
    private String services;
    private String gitBranch;
    private String gitCommit;
    private String imageTag;
    private String releaseNote;
    private Long operatorId;
    private Long reviewerId;
    private LocalDateTime plannedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime rollbackAt;
    private String rollbackReason;
    private String statusNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getReleaseNo() { return releaseNo; }
    public String getVersionName() { return versionName; }
    public String getEnvironment() { return environment; }
    public String getStatus() { return status; }
    public String getRiskLevel() { return riskLevel; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getServices() { return services; }
    public String getGitBranch() { return gitBranch; }
    public String getGitCommit() { return gitCommit; }
    public String getImageTag() { return imageTag; }
    public String getReleaseNote() { return releaseNote; }
    public Long getOperatorId() { return operatorId; }
    public Long getReviewerId() { return reviewerId; }
    public LocalDateTime getPlannedAt() { return plannedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getRollbackAt() { return rollbackAt; }
    public String getRollbackReason() { return rollbackReason; }
    public String getStatusNote() { return statusNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setReleaseNo(String releaseNo) { this.releaseNo = releaseNo; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setStatus(String status) { this.status = status; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setServices(String services) { this.services = services; }
    public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }
    public void setGitCommit(String gitCommit) { this.gitCommit = gitCommit; }
    public void setImageTag(String imageTag) { this.imageTag = imageTag; }
    public void setReleaseNote(String releaseNote) { this.releaseNote = releaseNote; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public void setPlannedAt(LocalDateTime plannedAt) { this.plannedAt = plannedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public void setRollbackAt(LocalDateTime rollbackAt) { this.rollbackAt = rollbackAt; }
    public void setRollbackReason(String rollbackReason) { this.rollbackReason = rollbackReason; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
