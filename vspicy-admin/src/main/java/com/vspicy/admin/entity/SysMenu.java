package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuCode;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private String permissionCode;
    private Integer sortNo;
    private Integer visible;
    private Integer status;
    private Integer editable;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getMenuCode() { return menuCode; }
    public String getMenuName() { return menuName; }
    public String getMenuType() { return menuType; }
    public String getPath() { return path; }
    public String getComponent() { return component; }
    public String getIcon() { return icon; }
    public String getPermissionCode() { return permissionCode; }
    public Integer getSortNo() { return sortNo; }
    public Integer getVisible() { return visible; }
    public Integer getStatus() { return status; }
    public Integer getEditable() { return editable; }
    public String getRemark() { return remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setMenuCode(String menuCode) { this.menuCode = menuCode; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
    public void setPath(String path) { this.path = path; }
    public void setComponent(String component) { this.component = component; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public void setVisible(Integer visible) { this.visible = visible; }
    public void setStatus(Integer status) { this.status = status; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
