package com.vspicy.admin.dto;

public record MenuOverviewView(
        long totalCount,
        long directoryCount,
        long menuCount,
        long buttonCount,
        long visibleCount,
        long hiddenCount,
        long enabledCount,
        long disabledCount
) {
}
