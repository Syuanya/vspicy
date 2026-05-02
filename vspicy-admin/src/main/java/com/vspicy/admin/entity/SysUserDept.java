package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user_dept")
public class SysUserDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long deptId;
    private Integer primaryFlag;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getDeptId() { return deptId; }
    public Integer getPrimaryFlag() { return primaryFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public void setPrimaryFlag(Integer primaryFlag) { this.primaryFlag = primaryFlag; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
