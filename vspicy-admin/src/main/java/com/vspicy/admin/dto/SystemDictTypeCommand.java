package com.vspicy.admin.dto;

public record SystemDictTypeCommand(
        String typeCode,
        String typeName,
        String description,
        Integer status,
        Boolean editable
) {
}
