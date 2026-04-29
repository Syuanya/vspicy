package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@ConditionalOnMissingBean(name = "adminOpsHubController")
@RequestMapping("/api/videos/admin/ops-hub")
public class AdminOpsHubCompatController {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeDispatcher dispatcher;

    public AdminOpsHubCompatController(JdbcTemplate jdbcTemplate, VideoTranscodeDispatcher dispatcher) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        List<Map<String, Object>> metrics = new ArrayList<>();
        addMetric(metrics, "transcode", "pending", "待转码", count("video_transcode_task", "status", "PENDING"), "info", "等待分发或执行的转码任务", "/admin/transcode-tasks");
        addMetric(metrics, "transcode", "running", "转码中", count("video_transcode_task", "status", "RUNNING"), "warning", "正在执行的转码任务", "/admin/transcode-tasks");
        addMetric(metrics, "transcode", "failed", "转码失败", count("video_transcode_task", "status", "FAILED"), "danger", "需要重试或重跑", "/admin/transcode-tasks");
        addMetric(metrics, "transcode", "success", "转码成功", count("video_transcode_task", "status", "SUCCESS"), "success", "已完成转码", "/admin/transcode-tasks");
        addMetric(metrics, "playbackReadiness", "problem", "播放就绪问题", 0L, "info", "兼容接口暂未批量计算，可进入页面扫描", "/admin/playback-readiness-batch");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("metrics", metrics);
        data.put("quickLinks", quickLinks());
        data.put("dispatchHealth", dispatcher.health());
        return Result.ok(data);
    }

    private List<Map<String, Object>> quickLinks() {
        return List.of(
                link("服务健康", "检查 MySQL / MinIO / RocketMQ / FFmpeg / 存储目录", "/admin/service-health", "danger", "video:service:health:view"),
                link("转码任务", "查看和操作转码任务", "/admin/transcode-tasks", "warning", "video:transcode:view"),
                link("播放就绪批量自愈", "扫描并同步播放地址", "/admin/playback-readiness-batch", "danger", "video:playback:readiness:view"),
                link("存储运维", "存储扫描、告警和一致性", "/admin/storage-ops", "info", "video:storage:ops:view")
        );
    }

    private Map<String, Object> link(String title, String desc, String link, String level, String permission) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title); m.put("description", desc); m.put("link", link); m.put("level", level); m.put("permissionCode", permission); return m;
    }

    private void addMetric(List<Map<String, Object>> list, String group, String key, String title, Long value, String level, String desc, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupKey", group); m.put("metricKey", key); m.put("title", title); m.put("value", value == null ? 0L : value); m.put("level", level); m.put("description", desc); m.put("link", link); list.add(m);
    }

    private Long count(String table, String column, String value) {
        if (!tableExists(table) || !columnExists(table, column)) return 0L;
        try { Long v = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `"+table+"` WHERE `"+column+"`=?", Long.class, value); return v == null ? 0L : v; } catch (Exception e) { return 0L; }
    }
    private boolean tableExists(String table) { try { Long v=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", Long.class, table); return v!=null&&v>0; } catch(Exception e){return false;} }
    private boolean columnExists(String table,String column) { try { Long v=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", Long.class, table, column); return v!=null&&v>0; } catch(Exception e){return false;} }
}
