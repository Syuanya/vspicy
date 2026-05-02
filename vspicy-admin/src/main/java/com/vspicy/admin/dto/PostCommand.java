package com.vspicy.admin.dto;

public record PostCommand(
        String postCode,
        String postName,
        Integer sortNo,
        Integer status,
        Boolean editable,
        String remark
) {
}
