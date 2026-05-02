package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record SystemDictTypeView(
        Long id,
        String typeCode,
        String typeName,
        String description,
        Integer status,
        Boolean editable,
        Long itemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
