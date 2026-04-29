package com.vspicy.admin.dto;

public record RoleCommand(
        String roleCode,
        String roleName,
        String description
) {
}
