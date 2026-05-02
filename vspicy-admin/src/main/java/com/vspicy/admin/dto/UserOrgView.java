package com.vspicy.admin.dto;

import java.util.List;

public record UserOrgView(
        Long userId,
        Long primaryDeptId,
        List<Long> deptIds,
        List<Long> postIds,
        List<DeptView> departments,
        List<PostView> posts
) {
}
