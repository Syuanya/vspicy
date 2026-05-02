package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_dept")
public class SysDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String deptCode;
    private String deptName;
    private String leaderName;
    private String leaderPhone;
    private Integer sortNo;
    private Integer status;
    private Integer editable;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getDeptCode() { return deptCode; }
    public String getDeptName() { return deptName; }
    public String getLeaderName() { return leaderName; }
    public String getLeaderPhone() { return leaderPhone; }
    public Integer getSortNo() { return sortNo; }
    public Integer getStatus() { return status; }
    public Integer getEditable() { return editable; }
    public String getRemark() { return remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setId(Long id) { this.id = id; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public void setLeaderPhone(String leaderPhone) { this.leaderPhone = leaderPhone; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public void setStatus(Integer status) { this.status = status; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
