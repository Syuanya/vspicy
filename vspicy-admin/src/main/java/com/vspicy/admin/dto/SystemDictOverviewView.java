package com.vspicy.admin.dto;

/**
 * 系统字典中心概览 DTO。
 */
public record SystemDictOverviewView(
        Long typeCount,
        Long enabledTypeCount,
        Long disabledTypeCount,
        Long itemCount,
        Long enabledItemCount,
        Long disabledItemCount
) {
}
