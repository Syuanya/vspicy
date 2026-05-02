package com.vspicy.admin.dto;

public record DeptCommand(
        Long parentId,
        String deptCode,
        String deptName,
        String leaderName,
        String leaderPhone,
        Integer sortNo,
        Integer status,
        Boolean editable,
        String remark
) {
}
