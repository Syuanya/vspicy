package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_job")
public class SysJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobCode;
    private String jobName;
    private String jobGroup;
    private String jobType;
    private String cronExpression;
    private String invokeTarget;
    private String jobParams;
    private String description;
    private Integer status;
    private Integer allowConcurrent;
    private Integer misfirePolicy;
    private Integer runCount;
    private Integer failCount;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private String lastRunStatus;
    private String lastError;
    private Integer editable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getInvokeTarget() { return invokeTarget; }
    public void setInvokeTarget(String invokeTarget) { this.invokeTarget = invokeTarget; }
    public String getJobParams() { return jobParams; }
    public void setJobParams(String jobParams) { this.jobParams = jobParams; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getAllowConcurrent() { return allowConcurrent; }
    public void setAllowConcurrent(Integer allowConcurrent) { this.allowConcurrent = allowConcurrent; }
    public Integer getMisfirePolicy() { return misfirePolicy; }
    public void setMisfirePolicy(Integer misfirePolicy) { this.misfirePolicy = misfirePolicy; }
    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public String getLastRunStatus() { return lastRunStatus; }
    public void setLastRunStatus(String lastRunStatus) { this.lastRunStatus = lastRunStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Integer getEditable() { return editable; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
