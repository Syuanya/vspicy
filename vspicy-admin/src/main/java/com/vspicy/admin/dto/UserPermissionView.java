package com.vspicy.admin.dto;

import com.vspicy.admin.entity.SysPermission;
import com.vspicy.admin.entity.SysRole;
import java.util.List;

public record UserPermissionView(
        Long userId,
        List<SysRole> roles,
        List<SysPermission> menus,
        List<String> permissionCodes
) {
}
