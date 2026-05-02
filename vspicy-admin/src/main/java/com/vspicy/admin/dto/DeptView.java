package com.vspicy.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeptView(
        Long id,
        Long parentId,
        String deptCode,
        String deptName,
        String leaderName,
        String leaderPhone,
        Integer sortNo,
        Integer status,
        Boolean editable,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DeptView> children
) {
}
