package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.client.MemberClient;
import com.vspicy.video.dto.VideoUploadQuotaCheckResponse;
import com.vspicy.video.dto.VideoUploadQuotaConfirmCommand;
import com.vspicy.video.dto.VideoUploadQuotaReleaseCommand;
import com.vspicy.video.dto.VideoUploadQuotaUsageView;
import com.vspicy.video.dto.VideoUploadRecordView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class VideoUploadQuotaService {
    private static final String DAILY = "DAILY";
    private static final String MONTHLY = "MONTHLY";
    private static final String TOTAL = "TOTAL";

    private final JdbcTemplate jdbcTemplate;
    private final MemberClient memberClient;

    public VideoUploadQuotaService(JdbcTemplate jdbcTemplate, MemberClient memberClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberClient = memberClient;
    }

    public VideoUploadQuotaUsageView usage(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        MemberUploadInfo member = memberUploadInfo(safeUserId, 0L);
        QuotaPolicy policy = policy(member.planCode(), member.maxUploadMb());

        long dailyUsed = usedMb(safeUserId, DAILY, dailyKey());
        long monthlyUsed = usedMb(safeUserId, MONTHLY, monthlyKey());
        long totalUsed = usedMb(safeUserId, TOTAL, TOTAL);

        return new VideoUploadQuotaUsageView(
                safeUserId,
                member.planCode(),
                policy.maxFileMb(),
                policy.dailyLimitMb(),
                dailyUsed,
                remaining(policy.dailyLimitMb(), dailyUsed),
                policy.monthlyLimitMb(),
                monthlyUsed,
                remaining(policy.monthlyLimitMb(), monthlyUsed),
                policy.totalLimitMb(),
                totalUsed,
                remaining(policy.totalLimitMb(), totalUsed)
        );
    }

    public List<VideoUploadRecordView> records(Long userId, Integer limit) {
        Long safeUserId = normalizeUserId(userId);
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 50 : limit;

        return jdbcTemplate.query("""
                SELECT id, user_id, video_id, file_name, size_mb, status,
                       created_at, released_at, release_reason
                FROM video_upload_record
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> new VideoUploadRecordView(
                rs.getLong("id"),
                rs.getLong("user_id"),
                getNullableLong(rs.getObject("video_id")),
                rs.getString("file_name"),
                rs.getLong("size_mb"),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("released_at"),
                rs.getString("release_reason")
        ), safeUserId, safeLimit);
    }

    public VideoUploadQuotaCheckResponse check(Long userId, Long sizeMb) {
        Long safeUserId = normalizeUserId(userId);
        long safeSizeMb = normalizeSize(sizeMb);

        MemberUploadInfo member = memberUploadInfo(safeUserId, safeSizeMb);
        QuotaPolicy policy = policy(member.planCode(), member.maxUploadMb());

        long dailyUsed = usedMb(safeUserId, DAILY, dailyKey());
        long monthlyUsed = usedMb(safeUserId, MONTHLY, monthlyKey());
        long totalUsed = usedMb(safeUserId, TOTAL, TOTAL);

        if (!member.fileAllowed()) {
            return response(
                    safeUserId,
                    member.planCode(),
                    safeSizeMb,
                    false,
                    member.reason(),
                    policy,
                    dailyUsed,
                    monthlyUsed,
                    totalUsed
            );
        }

        if (safeSizeMb > policy.maxFileMb()) {
            return response(
                    safeUserId,
                    member.planCode(),
                    safeSizeMb,
                    false,
                    "单文件超过当前等级上限 " + policy.maxFileMb() + "MB",
                    policy,
                    dailyUsed,
                    monthlyUsed,
                    totalUsed
            );
        }

        if (dailyUsed + safeSizeMb > policy.dailyLimitMb()) {
            return response(
                    safeUserId,
                    member.planCode(),
                    safeSizeMb,
                    false,
                    "超过今日上传配额，剩余 " + remaining(policy.dailyLimitMb(), dailyUsed) + "MB",
                    policy,
                    dailyUsed,
                    monthlyUsed,
                    totalUsed
            );
        }

        if (monthlyUsed + safeSizeMb > policy.monthlyLimitMb()) {
            return response(
                    safeUserId,
                    member.planCode(),
                    safeSizeMb,
                    false,
                    "超过本月上传配额，剩余 " + remaining(policy.monthlyLimitMb(), monthlyUsed) + "MB",
                    policy,
                    dailyUsed,
                    monthlyUsed,
                    totalUsed
            );
        }

        if (totalUsed + safeSizeMb > policy.totalLimitMb()) {
            return response(
                    safeUserId,
                    member.planCode(),
                    safeSizeMb,
                    false,
                    "超过总空间配额，剩余 " + remaining(policy.totalLimitMb(), totalUsed) + "MB",
                    policy,
                    dailyUsed,
                    monthlyUsed,
                    totalUsed
            );
        }

        return response(
                safeUserId,
                member.planCode(),
                safeSizeMb,
                true,
                "允许上传",
                policy,
                dailyUsed,
                monthlyUsed,
                totalUsed
        );
    }

    @Transactional
    public VideoUploadQuotaCheckResponse confirm(VideoUploadQuotaConfirmCommand command, Long headerUserId) {
        if (command == null) {
            throw new BizException("请求不能为空");
        }

        Long userId = command.userId() != null ? command.userId() : normalizeUserId(headerUserId);
        long sizeMb = normalizeSize(command.sizeMb());

        VideoUploadQuotaCheckResponse check = check(userId, sizeMb);
        if (!Boolean.TRUE.equals(check.allowed())) {
            throw new BizException(check.reason());
        }

        jdbcTemplate.update("""
                INSERT INTO video_upload_record(user_id, video_id, file_name, size_mb, status)
                VALUES (?, ?, ?, ?, 'CONFIRMED')
                """, userId, command.videoId(), command.fileName(), sizeMb);

        incrementUsage(userId, DAILY, dailyKey(), sizeMb);
        incrementUsage(userId, MONTHLY, monthlyKey(), sizeMb);
        incrementUsage(userId, TOTAL, TOTAL, sizeMb);

        return check(userId, 0L);
    }

    @Transactional
    public VideoUploadQuotaUsageView release(VideoUploadQuotaReleaseCommand command, Long headerUserId) {
        if (command == null) {
            throw new BizException("请求不能为空");
        }

        Long userId = normalizeUserId(headerUserId);
        UploadRecord record = findReleasableRecord(userId, command);
        String reason = command.reason() == null || command.reason().isBlank() ? "RELEASE" : command.reason();

        int updated = jdbcTemplate.update("""
                UPDATE video_upload_record
                SET status = 'RELEASED',
                    released_at = NOW(),
                    release_reason = ?
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'CONFIRMED'
                """, reason, record.id(), userId);

        if (updated <= 0) {
            throw new BizException("上传记录不存在或已释放");
        }

        decrementUsage(userId, DAILY, dailyKey(record.createdAt()), record.sizeMb());
        decrementUsage(userId, MONTHLY, monthlyKey(record.createdAt()), record.sizeMb());
        decrementUsage(userId, TOTAL, TOTAL, record.sizeMb());

        return usage(userId);
    }

    private UploadRecord findReleasableRecord(Long userId, VideoUploadQuotaReleaseCommand command) {
        String sql;
        Object[] params;

        if (command.recordId() != null) {
            sql = """
                    SELECT id, user_id, video_id, size_mb, status, created_at
                    FROM video_upload_record
                    WHERE id = ?
                      AND user_id = ?
                      AND status = 'CONFIRMED'
                    LIMIT 1
                    """;
            params = new Object[]{command.recordId(), userId};
        } else if (command.videoId() != null) {
            sql = """
                    SELECT id, user_id, video_id, size_mb, status, created_at
                    FROM video_upload_record
                    WHERE video_id = ?
                      AND user_id = ?
                      AND status = 'CONFIRMED'
                    ORDER BY id DESC
                    LIMIT 1
                    """;
            params = new Object[]{command.videoId(), userId};
        } else {
            throw new BizException("recordId 和 videoId 不能同时为空");
        }

        List<UploadRecord> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new UploadRecord(
                rs.getLong("id"),
                rs.getLong("user_id"),
                getNullableLong(rs.getObject("video_id")),
                rs.getLong("size_mb"),
                rs.getString("status"),
                rs.getString("created_at")
        ), params);

        if (rows.isEmpty()) {
            throw new BizException("上传记录不存在或已释放");
        }

        return rows.get(0);
    }

    private MemberUploadInfo memberUploadInfo(Long userId, Long sizeMb) {
        Map<String, Object> response = memberClient.checkUpload(userId, sizeMb == null ? 0L : sizeMb);
        if (response == null) {
            return new MemberUploadInfo("FREE", false, "会员服务无响应", 100L);
        }

        Object code = response.get("code");
        if (code != null && !"0".equals(String.valueOf(code))) {
            return new MemberUploadInfo("FREE", false, "会员服务校验失败：" + response.get("message"), 100L);
        }

        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return new MemberUploadInfo("FREE", false, "会员服务返回格式异常", 100L);
        }

        String planCode = stringValue(dataMap.get("planCode"));
        if (planCode == null || planCode.isBlank()) {
            planCode = "FREE";
        }

        Boolean allowed = Boolean.TRUE.equals(dataMap.get("allowed"));
        String reason = stringValue(dataMap.get("reason"));
        Long maxUploadMb = longValue(dataMap.get("maxUploadMb"));
        if (maxUploadMb == null || maxUploadMb <= 0) {
            maxUploadMb = switch (planCode) {
                case "VIP" -> 500L;
                case "CREATOR" -> 2048L;
                case "PRO" -> 10240L;
                default -> 100L;
            };
        }

        return new MemberUploadInfo(planCode, allowed, reason, maxUploadMb);
    }

    private QuotaPolicy policy(String planCode, Long memberMaxUploadMb) {
        String safePlanCode = planCode == null || planCode.isBlank() ? "FREE" : planCode;

        List<QuotaPolicy> rows = jdbcTemplate.query("""
                SELECT plan_code, max_file_mb, daily_limit_mb, monthly_limit_mb, total_limit_mb
                FROM video_upload_quota_policy
                WHERE plan_code = ?
                  AND status = 1
                LIMIT 1
                """, (rs, rowNum) -> new QuotaPolicy(
                rs.getString("plan_code"),
                rs.getLong("max_file_mb"),
                rs.getLong("daily_limit_mb"),
                rs.getLong("monthly_limit_mb"),
                rs.getLong("total_limit_mb")
        ), safePlanCode);

        if (!rows.isEmpty()) {
            return rows.get(0);
        }

        long maxFile = memberMaxUploadMb == null || memberMaxUploadMb <= 0 ? 100 : memberMaxUploadMb;
        return new QuotaPolicy("FREE", maxFile, 500L, 2048L, 5120L);
    }

    private long usedMb(Long userId, String scope, String scopeKey) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(used_mb), 0)
                FROM video_upload_quota_usage
                WHERE user_id = ?
                  AND quota_scope = ?
                  AND scope_key = ?
                """, Long.class, userId, scope, scopeKey);
        return value == null ? 0 : value;
    }

    private void incrementUsage(Long userId, String scope, String scopeKey, long sizeMb) {
        jdbcTemplate.update("""
                INSERT INTO video_upload_quota_usage(user_id, quota_scope, scope_key, used_mb, file_count)
                VALUES (?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                  used_mb = used_mb + VALUES(used_mb),
                  file_count = file_count + 1
                """, userId, scope, scopeKey, sizeMb);
    }

    private void decrementUsage(Long userId, String scope, String scopeKey, long sizeMb) {
        jdbcTemplate.update("""
                UPDATE video_upload_quota_usage
                SET used_mb = GREATEST(0, used_mb - ?),
                    file_count = GREATEST(0, file_count - 1)
                WHERE user_id = ?
                  AND quota_scope = ?
                  AND scope_key = ?
                """, sizeMb, userId, scope, scopeKey);
    }

    private VideoUploadQuotaCheckResponse response(
            Long userId,
            String planCode,
            Long fileSizeMb,
            Boolean allowed,
            String reason,
            QuotaPolicy policy,
            long dailyUsed,
            long monthlyUsed,
            long totalUsed
    ) {
        return new VideoUploadQuotaCheckResponse(
                userId,
                policy.planCode(),
                fileSizeMb,
                allowed,
                reason,
                policy.maxFileMb(),
                policy.dailyLimitMb(),
                dailyUsed,
                remaining(policy.dailyLimitMb(), dailyUsed),
                policy.monthlyLimitMb(),
                monthlyUsed,
                remaining(policy.monthlyLimitMb(), monthlyUsed),
                policy.totalLimitMb(),
                totalUsed,
                remaining(policy.totalLimitMb(), totalUsed)
        );
    }

    private long remaining(long limit, long used) {
        return Math.max(0, limit - used);
    }

    private Long normalizeUserId(Long userId) {
        return userId == null ? 1L : userId;
    }

    private long normalizeSize(Long sizeMb) {
        return sizeMb == null || sizeMb < 0 ? 0L : sizeMb;
    }

    private String dailyKey() {
        return LocalDate.now().toString();
    }

    private String monthlyKey() {
        return YearMonth.now().toString();
    }

    private String dailyKey(String createdAt) {
        return parseDate(createdAt).toString();
    }

    private String monthlyKey(String createdAt) {
        return YearMonth.from(parseDate(createdAt)).toString();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        String normalized = value.length() >= 10 ? value.substring(0, 10) : value;
        return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Long getNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record MemberUploadInfo(
            String planCode,
            Boolean fileAllowed,
            String reason,
            Long maxUploadMb
    ) {
    }

    private record QuotaPolicy(
            String planCode,
            Long maxFileMb,
            Long dailyLimitMb,
            Long monthlyLimitMb,
            Long totalLimitMb
    ) {
    }

    private record UploadRecord(
            Long id,
            Long userId,
            Long videoId,
            Long sizeMb,
            String status,
            String createdAt
    ) {
    }
}
