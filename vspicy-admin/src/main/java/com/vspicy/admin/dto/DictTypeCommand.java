package com.vspicy.admin.dto;

public record DictTypeCommand(
        String typeCode,
        String typeName,
        String description,
        Integer status,
        Boolean editable
) {
}
