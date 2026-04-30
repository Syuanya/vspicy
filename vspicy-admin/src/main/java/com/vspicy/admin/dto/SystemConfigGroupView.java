package com.vspicy.admin.dto;

public record SystemConfigGroupView(
        String groupCode,
        long totalCount,
        long enabledCount,
        long disabledCount
) {
}
