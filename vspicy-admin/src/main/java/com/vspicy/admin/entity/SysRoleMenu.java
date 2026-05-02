package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_role_menu")
public class SysRoleMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long menuId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getRoleId() { return roleId; }
    public Long getMenuId() { return menuId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setId(Long id) { this.id = id; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
