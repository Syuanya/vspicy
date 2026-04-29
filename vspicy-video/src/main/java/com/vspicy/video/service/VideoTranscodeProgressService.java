package com.vspicy.video.service;

import com.vspicy.video.constant.VideoTranscodeStatus;
import com.vspicy.video.dto.VideoTranscodeProgressView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VideoTranscodeProgressService {
    private final JdbcTemplate jdbcTemplate;

    public VideoTranscodeProgressService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public VideoTranscodeProgressView byVideoId(Long videoId) {
        if (videoId == null) {
            return unknown(null, null, "videoId 不能为空");
        }

        TaskRow task = latestTaskByVideoId(videoId);
        HlsRow hls = findHlsManifest(videoId);

        if (task == null) {
            return build(
                    videoId,
                    null,
                    "UNKNOWN",
                    0,
                    3,
                    null,
                    hls.ready(),
                    hls.manifestKey(),
                    hls.ready(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return fromTask(task, hls);
    }

    public VideoTranscodeProgressView byTaskId(Long taskId) {
        if (taskId == null) {
            return unknown(null, null, "taskId 不能为空");
        }

        TaskRow task = taskById(taskId);
        if (task == null) {
            return unknown(null, taskId, "未找到转码任务");
        }

        HlsRow hls = task.videoId() == null ? HlsRow.notReady() : findHlsManifest(task.videoId());
        return fromTask(task, hls);
    }

    private VideoTranscodeProgressView fromTask(TaskRow task, HlsRow hls) {
        return build(
                task.videoId(),
                task.id(),
                task.status(),
                task.retryCount(),
                task.maxRetryCount(),
                task.sourceFilePath(),
                hls.ready(),
                hls.manifestKey(),
                hls.ready() && VideoTranscodeStatus.SUCCESS.equals(task.status()),
                task.errorMessage(),
                task.lastDispatchMode(),
                task.lastDispatchError(),
                task.dispatchedAt(),
                task.startedAt(),
                task.finishedAt(),
                task.canceledAt(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private VideoTranscodeProgressView build(
            Long videoId,
            Long taskId,
            String status,
            Integer retryCount,
            Integer maxRetryCount,
            String sourceFilePath,
            Boolean hlsReady,
            String hlsManifestKey,
            Boolean playable,
            String errorMessage,
            String lastDispatchMode,
            String lastDispatchError,
            String dispatchedAt,
            String startedAt,
            String finishedAt,
            String canceledAt,
            String createdAt,
            String updatedAt
    ) {
        String displayStatus = displayStatus(status, hlsReady, playable);
        String displayMessage = displayMessage(status, hlsReady, playable, errorMessage, lastDispatchError);
        String suggestedAction = suggestedAction(status, hlsReady, playable);

        return new VideoTranscodeProgressView(
                videoId,
                taskId,
                status,
                retryCount == null ? 0 : retryCount,
                maxRetryCount == null ? 3 : maxRetryCount,
                sourceFilePath,
                hlsReady,
                hlsManifestKey,
                playable,
                displayStatus,
                displayMessage,
                suggestedAction,
                errorMessage,
                lastDispatchMode,
                lastDispatchError,
                dispatchedAt,
                startedAt,
                finishedAt,
                canceledAt,
                createdAt,
                updatedAt
        );
    }

    private VideoTranscodeProgressView unknown(Long videoId, Long taskId, String message) {
        return new VideoTranscodeProgressView(
                videoId,
                taskId,
                "UNKNOWN",
                0,
                3,
                null,
                false,
                null,
                false,
                "未找到转码任务",
                message,
                "等待任务创建或重新上传",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String displayStatus(String status, Boolean hlsReady, Boolean playable) {
        if (Boolean.TRUE.equals(playable)) {
            return "可播放";
        }

        if ("UNKNOWN".equals(status)) {
            return "未找到转码任务";
        }

        if (VideoTranscodeStatus.PENDING.equals(status)) {
            return "等待转码";
        }

        if (VideoTranscodeStatus.DISPATCHED.equals(status)) {
            return "已分发，等待执行";
        }

        if (VideoTranscodeStatus.RUNNING.equals(status)) {
            return "转码中";
        }

        if (VideoTranscodeStatus.SUCCESS.equals(status) && !Boolean.TRUE.equals(hlsReady)) {
            return "转码成功但 HLS 未就绪";
        }

        if (VideoTranscodeStatus.FAILED.equals(status)) {
            return "转码失败";
        }

        if (VideoTranscodeStatus.CANCELED.equals(status)) {
            return "转码已取消";
        }

        return status == null ? "未知状态" : status;
    }

    private String displayMessage(String status, Boolean hlsReady, Boolean playable, String errorMessage, String lastDispatchError) {
        if (Boolean.TRUE.equals(playable)) {
            return "HLS 已就绪，可以播放。";
        }

        if (VideoTranscodeStatus.PENDING.equals(status)) {
            return "任务正在等待分发或执行。";
        }

        if (VideoTranscodeStatus.DISPATCHED.equals(status)) {
            return "任务已分发，正在等待 RocketMQ 消费或本地 fallback。";
        }

        if (VideoTranscodeStatus.RUNNING.equals(status)) {
            return "视频正在转码，请稍后刷新。";
        }

        if (VideoTranscodeStatus.SUCCESS.equals(status) && !Boolean.TRUE.equals(hlsReady)) {
            return "转码任务状态为 SUCCESS，但没有检测到 m3u8 文件。建议强制重跑。";
        }

        if (VideoTranscodeStatus.FAILED.equals(status)) {
            String error = firstNotBlank(errorMessage, lastDispatchError);
            return error == null ? "转码失败，请查看服务日志。" : error;
        }

        if (VideoTranscodeStatus.CANCELED.equals(status)) {
            return "转码任务已取消，可在管理端重跑。";
        }

        return "暂未检测到可播放的 HLS 产物。";
    }

    private String suggestedAction(String status, Boolean hlsReady, Boolean playable) {
        if (Boolean.TRUE.equals(playable)) {
            return "PLAY";
        }

        if (VideoTranscodeStatus.SUCCESS.equals(status) && !Boolean.TRUE.equals(hlsReady)) {
            return "RERUN";
        }

        if (VideoTranscodeStatus.FAILED.equals(status) || VideoTranscodeStatus.CANCELED.equals(status)) {
            return "RETRY_OR_RERUN";
        }

        if (VideoTranscodeStatus.PENDING.equals(status) || VideoTranscodeStatus.DISPATCHED.equals(status) || VideoTranscodeStatus.RUNNING.equals(status)) {
            return "WAIT";
        }

        return "CHECK_TASK";
    }

    private TaskRow latestTaskByVideoId(Long videoId) {
        if (!tableExists("video_transcode_task") || !columnExists("video_transcode_task", "video_id")) {
            return null;
        }

        List<TaskRow> rows = jdbcTemplate.query("""
                SELECT id, video_id, source_file_path, status,
                       retry_count, max_retry_count, error_message,
                       last_dispatch_mode, last_dispatch_error,
                       dispatched_at, started_at, finished_at, canceled_at,
                       created_at, updated_at
                FROM video_transcode_task
                WHERE video_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, (rs, rowNum) -> mapTask(rs), videoId);

        return rows.isEmpty() ? null : rows.get(0);
    }

    private TaskRow taskById(Long taskId) {
        if (!tableExists("video_transcode_task")) {
            return null;
        }

        List<TaskRow> rows = jdbcTemplate.query("""
                SELECT id, video_id, source_file_path, status,
                       retry_count, max_retry_count, error_message,
                       last_dispatch_mode, last_dispatch_error,
                       dispatched_at, started_at, finished_at, canceled_at,
                       created_at, updated_at
                FROM video_transcode_task
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapTask(rs), taskId);

        return rows.isEmpty() ? null : rows.get(0);
    }

    private TaskRow mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskRow(
                rs.getLong("id"),
                nullableLong(rs.getObject("video_id")),
                rs.getString("source_file_path"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count"),
                rs.getString("error_message"),
                rs.getString("last_dispatch_mode"),
                rs.getString("last_dispatch_error"),
                rs.getString("dispatched_at"),
                rs.getString("started_at"),
                rs.getString("finished_at"),
                rs.getString("canceled_at"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private HlsRow findHlsManifest(Long videoId) {
        if (videoId == null || !tableExists("video_file") || !columnExists("video_file", "video_id")) {
            return HlsRow.notReady();
        }

        List<String> candidates = new ArrayList<>();
        addManifestCandidates(candidates, videoId, "object_key");
        addManifestCandidates(candidates, videoId, "file_path");
        addManifestCandidates(candidates, videoId, "url");
        addFileTypeCandidates(candidates, videoId);

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return new HlsRow(true, candidate);
            }
        }

        return HlsRow.notReady();
    }

    private void addManifestCandidates(List<String> candidates, Long videoId, String column) {
        if (!columnExists("video_file", column)) {
            return;
        }

        List<String> rows = jdbcTemplate.query(
                "SELECT `" + column + "` FROM video_file WHERE video_id = ? AND `" + column + "` LIKE '%.m3u8%' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(column),
                videoId
        );
        candidates.addAll(rows);
    }

    private void addFileTypeCandidates(List<String> candidates, Long videoId) {
        if (!columnExists("video_file", "file_type")) {
            return;
        }

        String selectColumn = firstExistingColumn("video_file", "object_key", "file_path", "url", "id");
        if (selectColumn == null) {
            return;
        }

        List<String> rows = jdbcTemplate.query(
                "SELECT `" + selectColumn + "` FROM video_file WHERE video_id = ? AND file_type IN ('HLS','M3U8','HLS_INDEX','MASTER_PLAYLIST') ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(selectColumn),
                videoId
        );
        candidates.addAll(rows);
    }

    private String firstExistingColumn(String table, String... columns) {
        for (String column : columns) {
            if (columnExists(table, column)) {
                return column;
            }
        }
        return null;
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

    private String firstNotBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record TaskRow(
            Long id,
            Long videoId,
            String sourceFilePath,
            String status,
            Integer retryCount,
            Integer maxRetryCount,
            String errorMessage,
            String lastDispatchMode,
            String lastDispatchError,
            String dispatchedAt,
            String startedAt,
            String finishedAt,
            String canceledAt,
            String createdAt,
            String updatedAt
    ) {
    }

    private record HlsRow(Boolean ready, String manifestKey) {
        static HlsRow notReady() {
            return new HlsRow(false, null);
        }
    }
}
