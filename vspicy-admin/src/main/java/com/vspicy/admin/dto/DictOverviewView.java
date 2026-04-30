package com.vspicy.admin.dto;

public record DictOverviewView(
        Long typeCount,
        Long enabledTypeCount,
        Long disabledTypeCount,
        Long itemCount,
        Long enabledItemCount,
        Long disabledItemCount
) {
}
