package com.vspicy.admin.dto;

import java.util.List;

public record UserOrgAssignCommand(
        List<Long> deptIds,
        Long primaryDeptId,
        List<Long> postIds
) {
}
