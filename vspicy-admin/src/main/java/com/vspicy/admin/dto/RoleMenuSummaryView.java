package com.vspicy.admin.dto;

import java.util.List;

public record RoleMenuSummaryView(
        Long roleId,
        String roleCode,
        String roleName,
        List<Long> menuIds,
        List<MenuView> menus
) {
}
