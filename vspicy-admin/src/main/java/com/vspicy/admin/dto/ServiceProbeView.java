package com.vspicy.admin.dto;

public record ServiceProbeView(
        String code,
        String name,
        String category,
        String host,
        Integer port,
        String endpoint,
        String status,
        Long latencyMillis,
        Boolean required,
        String ownerModule,
        String message,
        String startHint
) {
}
