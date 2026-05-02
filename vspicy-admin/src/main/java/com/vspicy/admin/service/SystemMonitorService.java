package com.vspicy.admin.service;

import com.vspicy.admin.dto.SystemMonitorDiskView;
import com.vspicy.admin.dto.SystemMonitorMemoryView;
import com.vspicy.admin.dto.SystemMonitorOverviewView;
import com.vspicy.admin.dto.SystemMonitorRuntimeProperty;
import com.vspicy.admin.dto.SystemMonitorThreadView;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemMonitorService {
    public SystemMonitorOverviewView overview() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        return new SystemMonitorOverviewView(
                hostName(),
                osBean.getName(),
                osBean.getArch(),
                osBean.getVersion(),
                osBean.getAvailableProcessors(),
                System.getProperty("java.version", ""),
                System.getProperty("java.vendor", ""),
                runtimeBean.getVmName(),
                System.getProperty("user.name", ""),
                System.getProperty("user.dir", ""),
                runtimeBean.getUptime(),
                formatDuration(runtimeBean.getUptime()),
                percentage(callDouble(osBean, "getProcessCpuLoad")),
                percentage(callDouble(osBean, "getSystemCpuLoad")),
                osBean.getSystemLoadAverage(),
                memory(memoryBean.getHeapMemoryUsage()),
                memory(memoryBean.getNonHeapMemoryUsage()),
                new SystemMonitorThreadView(
                        threadBean.getThreadCount(),
                        threadBean.getDaemonThreadCount(),
                        threadBean.getPeakThreadCount(),
                        threadBean.getTotalStartedThreadCount()
                ),
                disks(),
                runtimeProperties(),
                System.currentTimeMillis()
        );
    }

    private SystemMonitorMemoryView memory(MemoryUsage usage) {
        long used = Math.max(usage.getUsed(), 0L);
        long committed = Math.max(usage.getCommitted(), 0L);
        long max = Math.max(usage.getMax(), 0L);
        double rate = max > 0 ? round(used * 100.0 / max) : 0D;
        return new SystemMonitorMemoryView(used, committed, max, rate, bytes(used), bytes(committed), max > 0 ? bytes(max) : "不限制");
    }

    private List<SystemMonitorDiskView> disks() {
        List<SystemMonitorDiskView> rows = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) {
            return rows;
        }
        for (File root : roots) {
            long total = Math.max(root.getTotalSpace(), 0L);
            long free = Math.max(root.getFreeSpace(), 0L);
            long usable = Math.max(root.getUsableSpace(), 0L);
            long used = Math.max(total - free, 0L);
            double rate = total > 0 ? round(used * 100.0 / total) : 0D;
            rows.add(new SystemMonitorDiskView(root.getAbsolutePath(), total, free, usable, used, rate,
                    bytes(total), bytes(free), bytes(usable), bytes(used)));
        }
        return rows;
    }

    private List<SystemMonitorRuntimeProperty> runtimeProperties() {
        List<SystemMonitorRuntimeProperty> rows = new ArrayList<>();
        rows.add(new SystemMonitorRuntimeProperty("java.home", System.getProperty("java.home", "")));
        rows.add(new SystemMonitorRuntimeProperty("java.version", System.getProperty("java.version", "")));
        rows.add(new SystemMonitorRuntimeProperty("java.vendor", System.getProperty("java.vendor", "")));
        rows.add(new SystemMonitorRuntimeProperty("os.name", System.getProperty("os.name", "")));
        rows.add(new SystemMonitorRuntimeProperty("os.arch", System.getProperty("os.arch", "")));
        rows.add(new SystemMonitorRuntimeProperty("user.timezone", System.getProperty("user.timezone", "")));
        rows.add(new SystemMonitorRuntimeProperty("file.encoding", System.getProperty("file.encoding", "")));
        rows.add(new SystemMonitorRuntimeProperty("user.dir", System.getProperty("user.dir", "")));
        return rows;
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private double callDouble(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Number number) {
                double result = number.doubleValue();
                return result < 0 ? 0D : result;
            }
        } catch (Exception ignored) {
            // Some JVMs do not expose com.sun.management.OperatingSystemMXBean methods.
        }
        return 0D;
    }

    private double percentage(double value) {
        if (value <= 1D) {
            return round(value * 100D);
        }
        return round(value);
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int index = -1;
        do {
            value = value / 1024D;
            index++;
        } while (value >= 1024D && index < units.length - 1);
        return String.format("%.2f %s", value, units[index]);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        if (days > 0) {
            return days + "天 " + hours + "小时 " + minutes + "分钟";
        }
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        if (minutes > 0) {
            return minutes + "分钟 " + seconds + "秒";
        }
        return seconds + "秒";
    }
}
