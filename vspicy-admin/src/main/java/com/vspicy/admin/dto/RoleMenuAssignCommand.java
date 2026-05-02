package com.vspicy.admin.dto;

import java.util.List;

public record RoleMenuAssignCommand(
        List<Long> menuIds
) {
}
