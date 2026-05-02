package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_release_check_item")
public class SystemReleaseCheckItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long releaseId;
    private String checkName;
    private String checkType;
    private String status;
    private String resultNote;
    private Integer sortNo;
    private Long checkedBy;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getCheckName() { return checkName; }
    public String getCheckType() { return checkType; }
    public String getStatus() { return status; }
    public String getResultNote() { return resultNote; }
    public Integer getSortNo() { return sortNo; }
    public Long getCheckedBy() { return checkedBy; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setReleaseId(Long releaseId) { this.releaseId = releaseId; }
    public void setCheckName(String checkName) { this.checkName = checkName; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public void setStatus(String status) { this.status = status; }
    public void setResultNote(String resultNote) { this.resultNote = resultNote; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public void setCheckedBy(Long checkedBy) { this.checkedBy = checkedBy; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
