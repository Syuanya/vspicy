package com.vspicy.admin.dto;

import java.util.List;

public record SystemMonitorOverviewView(
        String hostName,
        String osName,
        String osArch,
        String osVersion,
        int availableProcessors,
        String javaVersion,
        String javaVendor,
        String vmName,
        String userName,
        String workDir,
        long uptimeMillis,
        String uptimeText,
        double processCpuLoad,
        double systemCpuLoad,
        double systemLoadAverage,
        SystemMonitorMemoryView heapMemory,
        SystemMonitorMemoryView nonHeapMemory,
        SystemMonitorThreadView thread,
        List<SystemMonitorDiskView> disks,
        List<SystemMonitorRuntimeProperty> runtimeProperties,
        long collectedAt
) {
}
