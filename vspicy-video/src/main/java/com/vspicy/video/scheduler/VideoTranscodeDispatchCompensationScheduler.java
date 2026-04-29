package com.vspicy.video.scheduler;

import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class VideoTranscodeDispatchCompensationScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeDispatcher dispatcher;
    private final boolean enabled;
    private final int limit;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VideoTranscodeDispatchCompensationScheduler(
            JdbcTemplate jdbcTemplate,
            VideoTranscodeDispatcher dispatcher,
            @Value("${vspicy.video.transcode.dispatch.compensation.enabled:false}") boolean enabled,
            @Value("${vspicy.video.transcode.dispatch.compensation.limit:10}") int limit
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.limit = limit <= 0 || limit > 100 ? 10 : limit;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.transcode.dispatch.compensation.fixed-delay-ms:60000}",
            initialDelayString = "${vspicy.video.transcode.dispatch.compensation.initial-delay-ms:30000}"
    )
    public void compensate() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            List<Long> ids = loadPendingTaskIds();
            for (Long id : ids) {
                try {
                    dispatcher.dispatch(id);
                } catch (Exception ex) {
                    System.err.println("转码分发补偿失败 taskId=" + id + ", error=" + ex.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    private List<Long> loadPendingTaskIds() {
        if (!tableExists("video_transcode_task") || !columnExists("video_transcode_task", "status")) {
            return List.of();
        }

        return jdbcTemplate.query("""
                SELECT id
                FROM video_transcode_task
                WHERE status IN ('PENDING', 'WAITING')
                ORDER BY id ASC
                LIMIT ?
                """, (rs, rowNum) -> rs.getLong("id"), limit);
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
