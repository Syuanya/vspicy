package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record PostView(
        Long id,
        String postCode,
        String postName,
        Integer sortNo,
        Integer status,
        Boolean editable,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
