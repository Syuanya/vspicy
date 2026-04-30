package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record DictItemView(
        Long id,
        String typeCode,
        String itemLabel,
        String itemValue,
        Integer sortNo,
        String cssClass,
        String extraJson,
        Integer status,
        Boolean editable,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
