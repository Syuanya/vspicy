package com.vspicy.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MenuView(
        Long id,
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
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MenuView> children
) {
}
