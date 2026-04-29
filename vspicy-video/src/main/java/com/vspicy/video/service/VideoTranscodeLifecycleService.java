package com.vspicy.video.service;

import com.vspicy.video.constant.VideoTranscodeStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VideoTranscodeLifecycleService {
    private final JdbcTemplate jdbcTemplate;

    public VideoTranscodeLifecycleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markDispatched(Long taskId, String mode, String errorMessage) {
        if (taskId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE video_transcode_task
                SET status = ?,
                    last_dispatch_mode = ?,
                    last_dispatch_error = ?,
                    dispatched_at = NOW(),
                    canceled_at = NULL
                WHERE id = ?
                  AND status NOT IN ('SUCCESS', 'CANCELED')
                """, VideoTranscodeStatus.DISPATCHED, mode, errorMessage, taskId);
    }

    public void markLocalFallbackDispatched(Long taskId, String mode, String errorMessage) {
        if (taskId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE video_transcode_task
                SET last_dispatch_mode = ?,
                    last_dispatch_error = ?,
                    dispatched_at = NOW(),
                    canceled_at = NULL
                WHERE id = ?
                  AND status NOT IN ('SUCCESS', 'CANCELED')
                """, mode, errorMessage, taskId);
    }

    public void markCanceled(Long taskId, String reason) {
        if (taskId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE video_transcode_task
                SET status = ?,
                    error_message = ?,
                    canceled_at = NOW()
                WHERE id = ?
                  AND status <> 'SUCCESS'
                """, VideoTranscodeStatus.CANCELED, reason == null ? "手动取消" : reason, taskId);
    }

    public void markSuccess(Long taskId) {
        if (taskId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE video_transcode_task
                SET status = ?,
                    error_message = NULL,
                    finished_at = NOW(),
                    last_dispatch_error = NULL
                WHERE id = ?
                """, VideoTranscodeStatus.SUCCESS, taskId);
    }

    public void markFailed(Long taskId, String reason) {
        if (taskId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE video_transcode_task
                SET status = ?,
                    error_message = ?,
                    finished_at = NOW()
                WHERE id = ?
                """, VideoTranscodeStatus.FAILED, reason == null ? "未知错误" : reason, taskId);
    }
}
