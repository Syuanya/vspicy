package com.vspicy.video.service;

import com.vspicy.video.dto.UserSpaceSummaryView;
import com.vspicy.video.dto.VideoUploadQuotaReconcileResult;
import com.vspicy.video.dto.VideoUploadRecordView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class VideoUserSpaceService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoUploadQuotaService quotaService;
    private final VideoUploadQuotaReconcileService reconcileService;

    public VideoUserSpaceService(
            JdbcTemplate jdbcTemplate,
            VideoUploadQuotaService quotaService,
            VideoUploadQuotaReconcileService reconcileService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.quotaService = quotaService;
        this.reconcileService = reconcileService;
    }

    public List<UserSpaceSummaryView> users(String keyword, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        String safeKeyword = keyword == null ? "" : keyword.trim();

        if (safeKeyword.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT
                      base.user_id,
                      su.username,
                      NULL AS nickname,
                      COALESCE(um.plan_code, 'FREE') AS plan_code,
                      COALESCE(daily.used_mb, 0) AS daily_used_mb,
                      COALESCE(monthly.used_mb, 0) AS monthly_used_mb,
                      COALESCE(total.used_mb, 0) AS total_used_mb,
                      COALESCE(record_stat.confirmed_count, 0) AS confirmed_record_count,
                      COALESCE(record_stat.released_count, 0) AS released_record_count,
                      COALESCE(record_stat.total_count, 0) AS total_record_count,
                      CASE
                        WHEN COALESCE(total.used_mb, 0) = COALESCE(record_stat.confirmed_mb, 0)
                        THEN 1 ELSE 0
                      END AS consistent
                    FROM (
                      SELECT id AS user_id FROM sys_user
                      UNION
                      SELECT user_id FROM video_upload_record
                      UNION
                      SELECT user_id FROM video_upload_quota_usage
                    ) base
                    LEFT JOIN sys_user su ON su.id = base.user_id
                    LEFT JOIN user_membership um
                      ON um.user_id = base.user_id
                     AND um.status = 'ACTIVE'
                     AND NOW() BETWEEN um.start_at AND um.end_at
                    LEFT JOIN video_upload_quota_usage daily
                      ON daily.user_id = base.user_id
                     AND daily.quota_scope = 'DAILY'
                     AND daily.scope_key = ?
                    LEFT JOIN video_upload_quota_usage monthly
                      ON monthly.user_id = base.user_id
                     AND monthly.quota_scope = 'MONTHLY'
                     AND monthly.scope_key = ?
                    LEFT JOIN video_upload_quota_usage total
                      ON total.user_id = base.user_id
                     AND total.quota_scope = 'TOTAL'
                     AND total.scope_key = 'TOTAL'
                    LEFT JOIN (
                      SELECT
                        user_id,
                        SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed_count,
                        SUM(CASE WHEN status = 'RELEASED' THEN 1 ELSE 0 END) AS released_count,
                        COUNT(*) AS total_count,
                        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN size_mb ELSE 0 END), 0) AS confirmed_mb
                      FROM video_upload_record
                      GROUP BY user_id
                    ) record_stat ON record_stat.user_id = base.user_id
                    ORDER BY total_used_mb DESC, base.user_id ASC
                    LIMIT ?
                    """, (rs, rowNum) -> new UserSpaceSummaryView(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("nickname"),
                    rs.getString("plan_code"),
                    rs.getLong("daily_used_mb"),
                    rs.getLong("monthly_used_mb"),
                    rs.getLong("total_used_mb"),
                    rs.getLong("confirmed_record_count"),
                    rs.getLong("released_record_count"),
                    rs.getLong("total_record_count"),
                    rs.getInt("consistent") == 1
            ), dailyKey(), monthlyKey(), safeLimit);
        }

        String likeKeyword = "%" + safeKeyword + "%";
        Long userIdKeyword = tryParseLong(safeKeyword);

        return jdbcTemplate.query("""
                SELECT
                  base.user_id,
                  su.username,
                  NULL AS nickname,
                  COALESCE(um.plan_code, 'FREE') AS plan_code,
                  COALESCE(daily.used_mb, 0) AS daily_used_mb,
                  COALESCE(monthly.used_mb, 0) AS monthly_used_mb,
                  COALESCE(total.used_mb, 0) AS total_used_mb,
                  COALESCE(record_stat.confirmed_count, 0) AS confirmed_record_count,
                  COALESCE(record_stat.released_count, 0) AS released_record_count,
                  COALESCE(record_stat.total_count, 0) AS total_record_count,
                  CASE
                    WHEN COALESCE(total.used_mb, 0) = COALESCE(record_stat.confirmed_mb, 0)
                    THEN 1 ELSE 0
                  END AS consistent
                FROM (
                  SELECT id AS user_id FROM sys_user
                  UNION
                  SELECT user_id FROM video_upload_record
                  UNION
                  SELECT user_id FROM video_upload_quota_usage
                ) base
                LEFT JOIN sys_user su ON su.id = base.user_id
                LEFT JOIN user_membership um
                  ON um.user_id = base.user_id
                 AND um.status = 'ACTIVE'
                 AND NOW() BETWEEN um.start_at AND um.end_at
                LEFT JOIN video_upload_quota_usage daily
                  ON daily.user_id = base.user_id
                 AND daily.quota_scope = 'DAILY'
                 AND daily.scope_key = ?
                LEFT JOIN video_upload_quota_usage monthly
                  ON monthly.user_id = base.user_id
                 AND monthly.quota_scope = 'MONTHLY'
                 AND monthly.scope_key = ?
                LEFT JOIN video_upload_quota_usage total
                  ON total.user_id = base.user_id
                 AND total.quota_scope = 'TOTAL'
                 AND total.scope_key = 'TOTAL'
                LEFT JOIN (
                  SELECT
                    user_id,
                    SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed_count,
                    SUM(CASE WHEN status = 'RELEASED' THEN 1 ELSE 0 END) AS released_count,
                    COUNT(*) AS total_count,
                    COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN size_mb ELSE 0 END), 0) AS confirmed_mb
                  FROM video_upload_record
                  GROUP BY user_id
                ) record_stat ON record_stat.user_id = base.user_id
                WHERE
                  (? IS NOT NULL AND base.user_id = ?)
                  OR su.username LIKE ?
                ORDER BY total_used_mb DESC, base.user_id ASC
                LIMIT ?
                """, (rs, rowNum) -> new UserSpaceSummaryView(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("plan_code"),
                rs.getLong("daily_used_mb"),
                rs.getLong("monthly_used_mb"),
                rs.getLong("total_used_mb"),
                rs.getLong("confirmed_record_count"),
                rs.getLong("released_record_count"),
                rs.getLong("total_record_count"),
                rs.getInt("consistent") == 1
        ), dailyKey(), monthlyKey(), userIdKeyword, userIdKeyword, likeKeyword, safeLimit);
    }

    public List<VideoUploadRecordView> records(Long userId, Integer limit) {
        return quotaService.records(userId, limit);
    }

    public VideoUploadQuotaReconcileResult reconcile(Long userId) {
        return reconcileService.reconcile(userId);
    }

    private String dailyKey() {
        return LocalDate.now().toString();
    }

    private String monthlyKey() {
        return YearMonth.now().toString();
    }

    private Long tryParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
