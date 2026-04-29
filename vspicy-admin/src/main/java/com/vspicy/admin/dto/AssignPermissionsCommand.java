package com.vspicy.admin.dto;

import java.util.List;

public record AssignPermissionsCommand(
        List<Long> permissionIds
) {
}
