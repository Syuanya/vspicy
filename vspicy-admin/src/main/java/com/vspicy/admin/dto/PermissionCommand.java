package com.vspicy.admin.dto;

public record PermissionCommand(
        Long parentId,
        String permissionCode,
        String permissionName,
        String permissionType,
        String path,
        String component,
        String icon,
        Integer sortNo,
        Integer status
) {
}
