package com.vspicy.admin.dto;

import java.util.List;

public record AssignRolesCommand(
        List<Long> roleIds
) {
}
