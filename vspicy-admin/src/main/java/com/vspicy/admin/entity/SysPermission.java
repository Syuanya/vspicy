package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String path;
    private String component;
    private String icon;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getPermissionCode() { return permissionCode; }
    public String getPermissionName() { return permissionName; }
    public String getPermissionType() { return permissionType; }
    public String getPath() { return path; }
    public String getComponent() { return component; }
    public String getIcon() { return icon; }
    public Integer getSortNo() { return sortNo; }
    public Integer getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setId(Long id) { this.id = id; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
    public void setPath(String path) { this.path = path; }
    public void setComponent(String component) { this.component = component; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public void setStatus(Integer status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
