package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.constant.VideoTranscodeStatus;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.dto.VideoTranscodeStateCommand;
import com.vspicy.video.dto.VideoTranscodeStateStatsView;
import com.vspicy.video.dto.VideoTranscodeTaskStateView;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VideoTranscodeStateService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeDispatcher dispatcher;
    private final VideoTranscodeLifecycleService lifecycleService;

    public VideoTranscodeStateService(
            JdbcTemplate jdbcTemplate,
            @Lazy VideoTranscodeDispatcher dispatcher,
            VideoTranscodeLifecycleService lifecycleService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
        this.lifecycleService = lifecycleService;
    }

    public VideoTranscodeStateStatsView stats() {
        return new VideoTranscodeStateStatsView(
                count(null),
                count(VideoTranscodeStatus.PENDING),
                count(VideoTranscodeStatus.DISPATCHED),
                count(VideoTranscodeStatus.RUNNING),
                count(VideoTranscodeStatus.SUCCESS),
                count(VideoTranscodeStatus.FAILED),
                count(VideoTranscodeStatus.CANCELED),
                retryExhaustedCount()
        );
    }

    public List<VideoTranscodeTaskStateView> list(String status, Integer limit) {
        int safeLimit = normalizeLimit(limit);
        String safeStatus = status == null ? "" : status.trim();

        String baseSql = """
                SELECT id, video_id, source_file_path, status,
                       retry_count, max_retry_count, error_message,
                       last_dispatch_mode, last_dispatch_error,
                       dispatched_at, started_at, finished_at, canceled_at,
                       created_at, updated_at
                FROM video_transcode_task
                """;

        if (safeStatus.isBlank()) {
            return jdbcTemplate.query(baseSql + " ORDER BY id DESC LIMIT ?", (rs, rowNum) -> map(rs), safeLimit);
        }

        return jdbcTemplate.query(baseSql + " WHERE status = ? ORDER BY id DESC LIMIT ?", (rs, rowNum) -> map(rs), safeStatus, safeLimit);
    }

    /**
     * Normal retry:
     * only retryable states are allowed. SUCCESS is intentionally blocked.
     */
    @Transactional
    public VideoTranscodeDispatchView retry(Long id, VideoTranscodeStateCommand command) {
        TaskSnapshot task = load(id);
        if (!VideoTranscodeStatus.isRetryable(task.status())) {
            throw new BizException("当前状态不可重试：" + task.status() + "。如需重新转码成功任务，请调用 /rerun");
        }
        if (task.retryCount() >= task.maxRetryCount()) {
            throw new BizException("已达到最大重试次数：" + task.retryCount() + "/" + task.maxRetryCount());
        }

        resetForDispatch(id, true);
        return dispatcher.dispatch(id);
    }

    /**
     * Force rerun:
     * available for any existing task, including SUCCESS.
     */
    @Transactional
    public VideoTranscodeDispatchView rerun(Long id, VideoTranscodeStateCommand command) {
        load(id);
        resetForDispatch(id, true);
        return dispatcher.dispatch(id);
    }

    @Transactional
    public VideoTranscodeTaskStateView cancel(Long id, VideoTranscodeStateCommand command) {
        TaskSnapshot task = load(id);
        if (!VideoTranscodeStatus.isCancelable(task.status())) {
            throw new BizException("当前状态不可取消：" + task.status());
        }
        lifecycleService.markCanceled(id, reason(command, "手动取消"));
        return findById(id);
    }

    @Transactional
    public VideoTranscodeTaskStateView reset(Long id, VideoTranscodeStateCommand command) {
        load(id);
        resetForDispatch(id, false);
        return findById(id);
    }

    @Transactional
    public VideoTranscodeTaskStateView success(Long id, VideoTranscodeStateCommand command) {
        load(id);
        lifecycleService.markSuccess(id);
        return findById(id);
    }

    @Transactional
    public VideoTranscodeTaskStateView fail(Long id, VideoTranscodeStateCommand command) {
        load(id);
        lifecycleService.markFailed(id, reason(command, "手动标记失败"));
        return findById(id);
    }

    public VideoTranscodeTaskStateView findById(Long id) {
        List<VideoTranscodeTaskStateView> rows = jdbcTemplate.query("""
                SELECT id, video_id, source_file_path, status,
                       retry_count, max_retry_count, error_message,
                       last_dispatch_mode, last_dispatch_error,
                       dispatched_at, started_at, finished_at, canceled_at,
                       created_at, updated_at
                FROM video_transcode_task
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> map(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("转码任务不存在：" + id);
        }
        return rows.get(0);
    }

    private void resetForDispatch(Long id, boolean increaseRetryCount) {
        if (increaseRetryCount) {
            jdbcTemplate.update("""
                    UPDATE video_transcode_task
                    SET status = 'PENDING',
                        retry_count = retry_count + 1,
                        error_message = NULL,
                        last_dispatch_error = NULL,
                        dispatched_at = NULL,
                        started_at = NULL,
                        finished_at = NULL,
                        canceled_at = NULL
                    WHERE id = ?
                    """, id);
            return;
        }

        jdbcTemplate.update("""
                UPDATE video_transcode_task
                SET status = 'PENDING',
                    error_message = NULL,
                    last_dispatch_mode = NULL,
                    last_dispatch_error = NULL,
                    dispatched_at = NULL,
                    started_at = NULL,
                    finished_at = NULL,
                    canceled_at = NULL
                WHERE id = ?
                """, id);
    }

    private TaskSnapshot load(Long id) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }
        List<TaskSnapshot> rows = jdbcTemplate.query("""
                SELECT id, status, retry_count, max_retry_count
                FROM video_transcode_task
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> new TaskSnapshot(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count")
        ), id);

        if (rows.isEmpty()) {
            throw new BizException("转码任务不存在：" + id);
        }
        return rows.get(0);
    }

    private long count(String status) {
        Long value;
        if (status == null) {
            value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_transcode_task", Long.class);
        } else {
            value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_transcode_task WHERE status = ?", Long.class, status);
        }
        return value == null ? 0L : value;
    }

    private long retryExhaustedCount() {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM video_transcode_task
                WHERE status = 'FAILED'
                  AND retry_count >= max_retry_count
                """, Long.class);
        return value == null ? 0L : value;
    }

    private VideoTranscodeTaskStateView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VideoTranscodeTaskStateView(
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

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 500 ? 100 : limit;
    }

    private String reason(VideoTranscodeStateCommand command, String defaultReason) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            return defaultReason;
        }
        return command.reason();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record TaskSnapshot(Long id, String status, Integer retryCount, Integer maxRetryCount) {
    }
}
