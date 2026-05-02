package com.vspicy.admin.dto;

public record SystemMonitorThreadView(
        int liveThreadCount,
        int daemonThreadCount,
        int peakThreadCount,
        long totalStartedThreadCount
) {
}
