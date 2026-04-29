package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.VideoUploadQuotaReconcilePreview;
import com.vspicy.video.dto.VideoUploadQuotaReconcileResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VideoUploadQuotaReconcileService {
    private final JdbcTemplate jdbcTemplate;

    public VideoUploadQuotaReconcileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public VideoUploadQuotaReconcilePreview preview(Long userId) {
        if (userId == null) {
            return new VideoUploadQuotaReconcilePreview(
                    null,
                    currentUsage("DAILY", null),
                    recordUsage("DAILY", null),
                    currentUsage("MONTHLY", null),
                    recordUsage("MONTHLY", null),
                    currentUsage("TOTAL", null),
                    recordUsage("TOTAL", null),
                    isConsistent(null)
            );
        }

        return new VideoUploadQuotaReconcilePreview(
                userId,
                currentUsage("DAILY", userId),
                recordUsage("DAILY", userId),
                currentUsage("MONTHLY", userId),
                recordUsage("MONTHLY", userId),
                currentUsage("TOTAL", userId),
                recordUsage("TOTAL", userId),
                isConsistent(userId)
        );
    }

    @Transactional
    public VideoUploadQuotaReconcileResult reconcile(Long userId) {
        if (userId == null) {
            return reconcileAll();
        }

        return reconcileUser(userId);
    }

    @Transactional
    public VideoUploadQuotaReconcileResult reconcileAll() {
        Long affectedUserCount = queryLong("""
                SELECT COUNT(DISTINCT user_id)
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                """);

        Long confirmedRecordCount = queryLong("""
                SELECT COUNT(*)
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                """);

        jdbcTemplate.update("DELETE FROM video_upload_quota_usage");

        int dailyRows = rebuildDaily(null);
        int monthlyRows = rebuildMonthly(null);
        int totalRows = rebuildTotal(null);

        return new VideoUploadQuotaReconcileResult(
                null,
                true,
                affectedUserCount,
                confirmedRecordCount,
                (long) dailyRows,
                (long) monthlyRows,
                (long) totalRows,
                "全量上传配额校准完成",
                LocalDateTime.now().toString()
        );
    }

    @Transactional
    public VideoUploadQuotaReconcileResult reconcileUser(Long userId) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }

        Long confirmedRecordCount = queryLong("""
                SELECT COUNT(*)
                FROM video_upload_record
                WHERE user_id = ?
                  AND status = 'CONFIRMED'
                """, userId);

        jdbcTemplate.update("""
                DELETE FROM video_upload_quota_usage
                WHERE user_id = ?
                """, userId);

        int dailyRows = rebuildDaily(userId);
        int monthlyRows = rebuildMonthly(userId);
        int totalRows = rebuildTotal(userId);

        return new VideoUploadQuotaReconcileResult(
                userId,
                false,
                confirmedRecordCount > 0 ? 1L : 0L,
                confirmedRecordCount,
                (long) dailyRows,
                (long) monthlyRows,
                (long) totalRows,
                "用户上传配额校准完成",
                LocalDateTime.now().toString()
        );
    }

    private int rebuildDaily(Long userId) {
        if (userId == null) {
            return jdbcTemplate.update("""
                    INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                    SELECT
                      user_id,
                      'DAILY' AS quota_scope,
                      DATE_FORMAT(created_at, '%Y-%m-%d') AS scope_key,
                      COALESCE(SUM(size_mb), 0) AS used_mb,
                      COUNT(*) AS file_count
                    FROM video_upload_record
                    WHERE status = 'CONFIRMED'
                    GROUP BY user_id, DATE_FORMAT(created_at, '%Y-%m-%d')
                    """);
        }

        return jdbcTemplate.update("""
                INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                SELECT
                  user_id,
                  'DAILY' AS quota_scope,
                  DATE_FORMAT(created_at, '%Y-%m-%d') AS scope_key,
                  COALESCE(SUM(size_mb), 0) AS used_mb,
                  COUNT(*) AS file_count
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                  AND user_id = ?
                GROUP BY user_id, DATE_FORMAT(created_at, '%Y-%m-%d')
                """, userId);
    }

    private int rebuildMonthly(Long userId) {
        if (userId == null) {
            return jdbcTemplate.update("""
                    INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                    SELECT
                      user_id,
                      'MONTHLY' AS quota_scope,
                      DATE_FORMAT(created_at, '%Y-%m') AS scope_key,
                      COALESCE(SUM(size_mb), 0) AS used_mb,
                      COUNT(*) AS file_count
                    FROM video_upload_record
                    WHERE status = 'CONFIRMED'
                    GROUP BY user_id, DATE_FORMAT(created_at, '%Y-%m')
                    """);
        }

        return jdbcTemplate.update("""
                INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                SELECT
                  user_id,
                  'MONTHLY' AS quota_scope,
                  DATE_FORMAT(created_at, '%Y-%m') AS scope_key,
                  COALESCE(SUM(size_mb), 0) AS used_mb,
                  COUNT(*) AS file_count
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                  AND user_id = ?
                GROUP BY user_id, DATE_FORMAT(created_at, '%Y-%m')
                """, userId);
    }

    private int rebuildTotal(Long userId) {
        if (userId == null) {
            return jdbcTemplate.update("""
                    INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                    SELECT
                      user_id,
                      'TOTAL' AS quota_scope,
                      'TOTAL' AS scope_key,
                      COALESCE(SUM(size_mb), 0) AS used_mb,
                      COUNT(*) AS file_count
                    FROM video_upload_record
                    WHERE status = 'CONFIRMED'
                    GROUP BY user_id
                    """);
        }

        return jdbcTemplate.update("""
                INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                SELECT
                  user_id,
                  'TOTAL' AS quota_scope,
                  'TOTAL' AS scope_key,
                  COALESCE(SUM(size_mb), 0) AS used_mb,
                  COUNT(*) AS file_count
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                  AND user_id = ?
                GROUP BY user_id
                """, userId);
    }

    private Long currentUsage(String scope, Long userId) {
        if (userId == null) {
            return queryLong("""
                    SELECT COALESCE(SUM(used_mb), 0)
                    FROM video_upload_quota_usage
                    WHERE quota_scope = ?
                    """, scope);
        }

        return queryLong("""
                SELECT COALESCE(SUM(used_mb), 0)
                FROM video_upload_quota_usage
                WHERE quota_scope = ?
                  AND user_id = ?
                """, scope, userId);
    }

    private Long recordUsage(String scope, Long userId) {
        String dateExpr = switch (scope) {
            case "DAILY" -> "DATE_FORMAT(created_at, '%Y-%m-%d')";
            case "MONTHLY" -> "DATE_FORMAT(created_at, '%Y-%m')";
            default -> "'TOTAL'";
        };

        if (userId == null) {
            return queryLong("""
                    SELECT COALESCE(SUM(size_mb), 0)
                    FROM video_upload_record
                    WHERE status = 'CONFIRMED'
                    """);
        }

        return queryLong("""
                SELECT COALESCE(SUM(size_mb), 0)
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                  AND user_id = ?
                """, userId);
    }

    private boolean isConsistent(Long userId) {
        return currentUsage("DAILY", userId).equals(recordUsage("DAILY", userId))
                && currentUsage("MONTHLY", userId).equals(recordUsage("MONTHLY", userId))
                && currentUsage("TOTAL", userId).equals(recordUsage("TOTAL", userId));
    }

    private Long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
