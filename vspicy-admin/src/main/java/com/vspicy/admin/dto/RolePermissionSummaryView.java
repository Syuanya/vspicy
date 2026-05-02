package com.vspicy.admin.dto;

import com.vspicy.admin.entity.SysPermission;
import com.vspicy.admin.entity.SysRole;

import java.util.List;

public record RolePermissionSummaryView(
        SysRole role,
        List<SysPermission> permissions,
        List<Long> permissionIds,
        int permissionCount
) {
}
