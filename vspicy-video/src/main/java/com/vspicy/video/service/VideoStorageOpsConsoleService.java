package com.vspicy.video.service;

import com.vspicy.video.dto.StorageOpsLinkView;
import com.vspicy.video.dto.VideoStorageDashboardView;
import com.vspicy.video.dto.VideoStorageOpsConsoleView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VideoStorageOpsConsoleService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoStorageDashboardService dashboardService;

    public VideoStorageOpsConsoleService(
            JdbcTemplate jdbcTemplate,
            VideoStorageDashboardService dashboardService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dashboardService = dashboardService;
    }

    public VideoStorageOpsConsoleView console(String prefix, Integer limit, Integer threshold) {
        VideoStorageDashboardView dashboard = dashboardService.dashboard(prefix, limit, threshold);

        return new VideoStorageOpsConsoleView(
                dashboard.bucket(),
                dashboard.prefix(),
                dashboard.limit(),
                dashboard.threshold(),
                dashboard,

                count("video_storage_alert_event", "status", "OPEN"),
                count("video_storage_alert_event", "status", "ACKED"),
                count("video_storage_alert_event", "status", "RESOLVED"),
                countAlertLevel("CRITICAL"),
                countAlertLevel("HIGH"),
                countAlertLevel("WARN"),

                count("video_hls_repair_task", "status", "PENDING"),
                count("video_hls_repair_task", "status", "DISPATCHED"),
                count("video_hls_repair_task", "status", "RUNNING"),
                count("video_hls_repair_task", "status", "SUCCESS"),
                count("video_hls_repair_task", "status", "FAILED"),
                count("video_hls_repair_task", "status", "CANCELED"),

                count("video_object_cleanup_request", "status", "PENDING"),
                count("video_object_cleanup_request", "status", "APPROVED"),
                count("video_object_cleanup_request", "status", "REJECTED"),
                count("video_object_cleanup_request", "status", "EXECUTED"),
                count("video_object_cleanup_request", "status", "FAILED"),

                links()
        );
    }

    private long countAlertLevel(String level) {
        if (!tableExists("video_storage_alert_event")) {
            return 0L;
        }

        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM video_storage_alert_event
                WHERE status = 'OPEN'
                  AND alert_level = ?
                """, Long.class, level);

        return value == null ? 0L : value;
    }

    private long count(String table, String column, String value) {
        if (!safeName(table) || !safeName(column) || !tableExists(table) || !columnExists(table, column)) {
            return 0L;
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` = ?",
                Long.class,
                value
        );

        return count == null ? 0L : count;
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Long.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Long.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean safeName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]+");
    }

    private List<StorageOpsLinkView> links() {
        return List.of(
                new StorageOpsLinkView("存储大屏", "/admin/storage-dashboard", "查看 MinIO、DB objectKey、空间 Top、容量告警。", "INFO"),
                new StorageOpsLinkView("文件一致性", "/admin/video-file-consistency", "检查 video_file、upload_trace、MinIO 对象是否一致。", "INFO"),
                new StorageOpsLinkView("HLS完整性", "/admin/hls-integrity", "检查 index.m3u8 和分片是否完整。", "WARN"),
                new StorageOpsLinkView("存储告警", "/admin/storage-alerts", "生成和确认容量、孤儿对象、HLS 缺失等告警。", "WARN"),
                new StorageOpsLinkView("告警通知", "/admin/storage-alert-notifications", "将存储告警桥接到站内通知。", "INFO"),
                new StorageOpsLinkView("HLS修复", "/admin/hls-repair", "生成、分发、执行、复检 HLS 修复任务。", "HIGH"),
                new StorageOpsLinkView("对象清理", "/admin/object-cleanup", "孤儿对象审批后删除，默认 dryRun。", "HIGH"),
                new StorageOpsLinkView("用户空间", "/admin/user-space", "查看用户空间占用和上传记录。", "INFO"),
                new StorageOpsLinkView("上传配额", "/admin/video-upload-quota", "查看和校准上传配额。", "INFO")
        );
    }
}
