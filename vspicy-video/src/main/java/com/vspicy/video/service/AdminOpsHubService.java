package com.vspicy.video.service;

import com.vspicy.video.dto.*;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminOpsHubService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeDispatcher transcodeDispatcher;
    private final VideoPlaybackReadinessBatchService playbackReadinessBatchService;

    public AdminOpsHubService(
            JdbcTemplate jdbcTemplate,
            VideoTranscodeDispatcher transcodeDispatcher,
            VideoPlaybackReadinessBatchService playbackReadinessBatchService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transcodeDispatcher = transcodeDispatcher;
        this.playbackReadinessBatchService = playbackReadinessBatchService;
    }

    public AdminOpsHubSummaryView summary() {
        List<AdminOpsHubMetricView> metrics = new ArrayList<>();

        addTranscodeMetrics(metrics);
        addHlsRepairMetrics(metrics);
        addCleanupMetrics(metrics);
        addAlertMetrics(metrics);
        addPlaybackReadinessMetrics(metrics);
        addOperationAuditMetrics(metrics);

        return new AdminOpsHubSummaryView(
                LocalDateTime.now().toString(),
                metrics,
                quickLinks(),
                safeDispatchHealth()
        );
    }

    private void addTranscodeMetrics(List<AdminOpsHubMetricView> metrics) {
        String table = "video_transcode_task";
        if (!tableExists(table)) {
            metrics.add(metric("transcode", "missing", "转码任务表缺失", 0L, "danger", "video_transcode_task 不存在", "/admin/transcode-tasks"));
            return;
        }

        metrics.add(metric("transcode", "pending", "待转码", count(table, "status", "PENDING"), "info", "等待分发或执行的转码任务", "/admin/transcode-tasks"));
        metrics.add(metric("transcode", "dispatched", "已分发", count(table, "status", "DISPATCHED"), "info", "已分发等待消费的任务", "/admin/transcode-tasks"));
        metrics.add(metric("transcode", "running", "转码中", count(table, "status", "RUNNING"), "warning", "正在执行 FFmpeg 的任务", "/admin/transcode-tasks"));
        metrics.add(metric("transcode", "failed", "转码失败", count(table, "status", "FAILED"), "danger", "需要重试或重跑的任务", "/admin/transcode-tasks"));
        metrics.add(metric("transcode", "success", "转码成功", count(table, "status", "SUCCESS"), "success", "已成功转码的任务", "/admin/transcode-tasks"));
    }

    private void addHlsRepairMetrics(List<AdminOpsHubMetricView> metrics) {
        String table = "video_hls_repair_task";
        if (!tableExists(table)) {
            metrics.add(metric("hlsRepair", "missing", "HLS 修复表缺失", 0L, "warning", "video_hls_repair_task 不存在", "/admin/hls-repair"));
            return;
        }

        metrics.add(metric("hlsRepair", "pending", "HLS 待修复", count(table, "status", "PENDING"), "warning", "等待处理的 HLS 修复任务", "/admin/hls-repair"));
        metrics.add(metric("hlsRepair", "failed", "HLS 修复失败", count(table, "status", "FAILED"), "danger", "修复失败，需要人工介入", "/admin/hls-repair"));
        metrics.add(metric("hlsRepair", "success", "HLS 修复成功", count(table, "status", "SUCCESS"), "success", "已完成修复的任务", "/admin/hls-repair"));
    }

    private void addCleanupMetrics(List<AdminOpsHubMetricView> metrics) {
        String table = "video_object_cleanup_request";
        if (!tableExists(table)) {
            metrics.add(metric("cleanup", "missing", "对象清理表缺失", 0L, "warning", "video_object_cleanup_request 不存在", "/admin/object-cleanup"));
            return;
        }

        metrics.add(metric("cleanup", "pending", "清理待审批", count(table, "status", "PENDING"), "warning", "等待审批的对象清理申请", "/admin/object-cleanup"));
        metrics.add(metric("cleanup", "approved", "清理已批准", count(table, "status", "APPROVED"), "info", "等待执行删除的申请", "/admin/object-cleanup"));
        metrics.add(metric("cleanup", "failed", "清理失败", count(table, "status", "FAILED"), "danger", "执行失败的清理申请", "/admin/object-cleanup"));
        metrics.add(metric("cleanup", "executed", "清理已执行", count(table, "status", "EXECUTED"), "success", "已执行的清理申请", "/admin/object-cleanup"));
    }

    private void addAlertMetrics(List<AdminOpsHubMetricView> metrics) {
        String table = "video_storage_alert_event";
        if (!tableExists(table)) {
            metrics.add(metric("storageAlert", "missing", "存储告警表缺失", 0L, "warning", "video_storage_alert_event 不存在", "/admin/storage-ops"));
            return;
        }

        metrics.add(metric("storageAlert", "open", "未处理告警", count(table, "status", "OPEN"), "danger", "仍然打开的存储告警", "/admin/storage-ops"));
        metrics.add(metric("storageAlert", "acked", "已确认告警", count(table, "status", "ACKED"), "warning", "已确认但未关闭的告警", "/admin/storage-ops"));
        metrics.add(metric("storageAlert", "resolved", "已解决告警", count(table, "status", "RESOLVED"), "success", "已关闭的存储告警", "/admin/storage-ops"));
    }

    private void addPlaybackReadinessMetrics(List<AdminOpsHubMetricView> metrics) {
        try {
            VideoPlaybackReadinessBatchResult result = playbackReadinessBatchService.scan(100, true);
            metrics.add(metric(
                    "playbackReadiness",
                    "problem",
                    "播放就绪问题",
                    result.problemCount() == null ? 0L : result.problemCount().longValue(),
                    result.problemCount() != null && result.problemCount() > 0 ? "danger" : "success",
                    "最近 100 个视频中有 HLS 但 video 状态或播放地址未同步的问题数量",
                    "/admin/playback-readiness-batch"
            ));
        } catch (Exception ex) {
            metrics.add(metric(
                    "playbackReadiness",
                    "error",
                    "播放就绪扫描失败",
                    0L,
                    "warning",
                    ex.getMessage(),
                    "/admin/playback-readiness-batch"
            ));
        }
    }


    private void addOperationAuditMetrics(List<AdminOpsHubMetricView> metrics) {
        String table = "operation_audit_log";
        if (!tableExists(table)) {
            metrics.add(metric("operationAudit", "missing", "审计表缺失", 0L, "warning", "operation_audit_log 不存在", "/admin/operation-audit"));
            return;
        }

        Long rejectedCount = countRejectedAuditActions();
        Long dangerCount = countDangerAuditActions();
        metrics.add(metric(
                "operationAudit",
                "rejected",
                "被拒绝高危操作",
                rejectedCount,
                rejectedCount > 0 ? "danger" : "success",
                "reason / 确认短语校验失败并被后端拒绝的操作",
                "/admin/operation-audit"
        ));
        metrics.add(metric(
                "operationAudit",
                "danger",
                "高危操作总量",
                dangerCount,
                dangerCount > 0 ? "warning" : "success",
                "包含重跑、取消、重置、同步、清理、修复等高风险操作",
                "/admin/operation-audit"
        ));

        if (tableExists("operation_audit_alert_event")) {
            Long openAlertCount = count("operation_audit_alert_event", "status", "OPEN");
            Long ackedAlertCount = count("operation_audit_alert_event", "status", "ACKED");
            metrics.add(metric(
                    "operationAudit",
                    "alertOpen",
                    "审计告警待处理",
                    openAlertCount,
                    openAlertCount > 0 ? "danger" : "success",
                    "已同步到 operation_audit_alert_event 且仍未处理的审计告警",
                    "/admin/operation-audit"
            ));
            metrics.add(metric(
                    "operationAudit",
                    "alertAcked",
                    "审计告警已确认",
                    ackedAlertCount,
                    ackedAlertCount > 0 ? "warning" : "success",
                    "已确认但尚未解决的审计告警",
                    "/admin/operation-audit"
            ));
        }
    }

    private VideoTranscodeDispatchView safeDispatchHealth() {
        try {
            return transcodeDispatcher.health();
        } catch (Exception ex) {
            return new VideoTranscodeDispatchView(
                    null,
                    "HEALTH_ERROR",
                    null,
                    null,
                    null,
                    false,
                    false,
                    true,
                    false,
                    false,
                    "转码分发健康检查失败",
                    ex.getMessage()
            );
        }
    }

    private List<AdminOpsHubQuickLinkView> quickLinks() {
        return List.of(
                new AdminOpsHubQuickLinkView(
                        "存储运维总控台",
                        "查看存储扫描、告警、对象一致性和运维指标",
                        "/admin/storage-ops",
                        "info",
                        "video:storage:ops:view"
                ),
                new AdminOpsHubQuickLinkView(
                        "转码任务",
                        "查看和操作转码任务状态机、重试、重跑、本地分发",
                        "/admin/transcode-tasks",
                        "warning",
                        "video:transcode:state:view"
                ),
                new AdminOpsHubQuickLinkView(
                        "播放就绪批量自愈",
                        "批量扫描并同步 HLS 已生成但 video 状态/播放地址未同步的视频",
                        "/admin/playback-readiness-batch",
                        "danger",
                        "video:playback:readiness:sync"
                ),
                new AdminOpsHubQuickLinkView(
                        "HLS 修复任务",
                        "生成、分发、执行和复检 HLS 产物修复任务",
                        "/admin/hls-repair",
                        "warning",
                        "video:hls:repair:view"
                ),
                new AdminOpsHubQuickLinkView(
                        "对象清理审批",
                        "处理 MinIO 中数据库缺失对象的清理申请",
                        "/admin/object-cleanup",
                        "info",
                        "video:object:cleanup:view"
                )
        );
    }

    private AdminOpsHubMetricView metric(
            String groupKey,
            String metricKey,
            String title,
            Long value,
            String level,
            String description,
            String link
    ) {
        return new AdminOpsHubMetricView(groupKey, metricKey, title, value == null ? 0L : value, level, description, link);
    }


    private Long countRejectedAuditActions() {
        if (!tableExists("operation_audit_log")) {
            return 0L;
        }
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM operation_audit_log
                    WHERE RIGHT(action, 9) = '_REJECTED'
                       OR REPLACE(detail_json, ' ', '') LIKE '%\"rejected\":true%'
                    """, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private Long countDangerAuditActions() {
        if (!tableExists("operation_audit_log")) {
            return 0L;
        }
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM operation_audit_log
                    WHERE RIGHT(action, 9) = '_REJECTED'
                       OR action LIKE '%RERUN%'
                       OR action LIKE '%RESET%'
                       OR action LIKE '%CANCEL%'
                       OR action LIKE '%FAIL%'
                       OR action LIKE '%SYNC%'
                       OR action LIKE '%CLEANUP%'
                       OR action LIKE '%REPAIR%'
                    """, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private Long count(String tableName, String columnName, String value) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return 0L;
        }

        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + tableName + "` WHERE `" + columnName + "` = ?",
                    Long.class,
                    value
            );
            return count == null ? 0L : count;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """, Long.class, tableName);
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND column_name = ?
                    """, Long.class, tableName, columnName);
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
