package com.vspicy.admin.dto;

public record PermissionOverviewView(
        long roleTotal,
        long enabledRoleTotal,
        long disabledRoleTotal,
        long permissionTotal,
        long enabledPermissionTotal,
        long disabledPermissionTotal,
        long menuPermissionTotal,
        long buttonPermissionTotal,
        long apiPermissionTotal,
        long userRoleBindingTotal,
        long rolePermissionBindingTotal
) {
}
