package com.vspicy.admin.dto;

public record DictItemCommand(
        String typeCode,
        String itemLabel,
        String itemValue,
        Integer sortNo,
        String cssClass,
        String extraJson,
        Integer status,
        Boolean editable,
        String remark
) {
}
