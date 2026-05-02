package com.vspicy.admin.dto;

public record MenuCommand(
        Long parentId,
        String menuCode,
        String menuName,
        String menuType,
        String path,
        String component,
        String icon,
        String permissionCode,
        Integer sortNo,
        Boolean visible,
        Integer status,
        Boolean editable,
        String remark
) {
}
