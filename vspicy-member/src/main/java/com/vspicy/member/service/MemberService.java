package com.vspicy.member.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.member.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MemberService {
    private final JdbcTemplate jdbcTemplate;
    private final MemberCacheService cacheService;

    public MemberService(JdbcTemplate jdbcTemplate, MemberCacheService cacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheService = cacheService;
    }

    public List<MemberPlanView> plans() {
        return jdbcTemplate.query("""
                SELECT id, plan_code, plan_name, description, price_cent, duration_days, level_no, max_upload_mb
                FROM member_plan
                WHERE status = 1
                ORDER BY level_no ASC
                """, (rs, rowNum) -> new MemberPlanView(
                rs.getLong("id"),
                rs.getString("plan_code"),
                rs.getString("plan_name"),
                rs.getString("description"),
                rs.getLong("price_cent"),
                rs.getInt("duration_days"),
                rs.getInt("level_no"),
                rs.getLong("max_upload_mb")
        ));
    }

    public UserMembershipView current(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        return cacheService.getMembership(safeUserId)
                .orElseGet(() -> {
                    UserMembershipView view = currentNoCache(safeUserId);
                    cacheService.putMembership(safeUserId, view);
                    return view;
                });
    }

    public UserMembershipView refreshCache(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        cacheService.evictMembership(safeUserId);
        UserMembershipView view = currentNoCache(safeUserId);
        cacheService.putMembership(safeUserId, view);
        return view;
    }

    public void evictCache(Long userId) {
        cacheService.evictMembership(normalizeUserId(userId));
    }

    @Transactional
    public UserMembershipView subscribe(Long userId, SubscribeCommand command) {
        Long safeUserId = command != null && command.userId() != null ? command.userId() : normalizeUserId(userId);

        if (command == null || command.planCode() == null || command.planCode().isBlank()) {
            throw new BizException("planCode 不能为空");
        }

        String planCode = command.planCode().trim().toUpperCase();
        if ("FREE".equals(planCode)) {
            throw new BizException("FREE 套餐不需要开通");
        }

        MemberPlanView plan = plan(planCode);
        int months = command.months() == null || command.months() <= 0 ? 1 : command.months();
        int durationDays = plan.durationDays() <= 0 ? 31 : plan.durationDays();
        int totalDays = durationDays * months;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = now;
        LocalDateTime endAt = now.plusDays(totalDays);

        MembershipRow existing = findActiveMembership(safeUserId);
        if (existing != null && existing.active()) {
            startAt = parseDateTime(existing.startAt());
            endAt = parseDateTime(existing.endAt()).plusDays(totalDays);
        }

        String orderNo = "M" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO member_order(order_no, user_id, plan_code, amount_cent, status, pay_channel, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 'DEV', NOW())
                """, orderNo, safeUserId, planCode, plan.priceCent() * months);

        jdbcTemplate.update("""
                INSERT INTO user_membership(user_id, plan_code, status, start_at, end_at, source)
                VALUES (?, ?, 'ACTIVE', ?, ?, 'DEV')
                ON DUPLICATE KEY UPDATE
                  plan_code = VALUES(plan_code),
                  status = 'ACTIVE',
                  start_at = VALUES(start_at),
                  end_at = VALUES(end_at),
                  source = 'DEV'
                """, safeUserId, planCode, startAt, endAt);

        cacheService.evictMembership(safeUserId);
        return current(safeUserId);
    }

    @Transactional
    public UserMembershipView cancel(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        jdbcTemplate.update("""
                UPDATE user_membership
                SET status = 'CANCELED'
                WHERE user_id = ?
                """, safeUserId);

        cacheService.evictMembership(safeUserId);
        return current(safeUserId);
    }

    public List<MemberBenefitView> benefits(Long userId) {
        return current(normalizeUserId(userId)).benefits();
    }

    public MemberCheckResponse checkHd(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        UserMembershipView membership = current(safeUserId);
        boolean allowed = membership.benefits().stream().anyMatch(item -> "HD_PLAY".equals(item.benefitCode()));
        return new MemberCheckResponse(
                safeUserId,
                membership.planCode(),
                allowed,
                allowed ? "允许高清播放" : "当前会员等级不支持高清播放",
                membership.maxUploadMb()
        );
    }

    public MemberCheckResponse checkUpload(Long userId, Long sizeMb) {
        Long safeUserId = normalizeUserId(userId);
        long safeSizeMb = sizeMb == null || sizeMb < 0 ? 0 : sizeMb;
        UserMembershipView membership = current(safeUserId);
        boolean allowed = safeSizeMb <= membership.maxUploadMb();

        return new MemberCheckResponse(
                safeUserId,
                membership.planCode(),
                allowed,
                allowed ? "允许上传" : "文件过大，当前等级最大允许 " + membership.maxUploadMb() + "MB",
                membership.maxUploadMb()
        );
    }

    private UserMembershipView currentNoCache(Long userId) {
        MembershipRow row = currentMembershipRow(userId);
        MemberPlanView plan = plan(row.planCode());
        List<MemberBenefitView> benefits = benefitsByPlan(row.planCode());

        return new UserMembershipView(
                userId,
                row.planCode(),
                plan.planName(),
                row.status(),
                row.startAt(),
                row.endAt(),
                row.active(),
                plan.maxUploadMb(),
                benefits
        );
    }

    private MembershipRow currentMembershipRow(Long userId) {
        MembershipRow active = findActiveMembership(userId);
        if (active != null) {
            return active;
        }

        return new MembershipRow(
                userId,
                "FREE",
                "ACTIVE",
                null,
                null,
                true
        );
    }

    private MembershipRow findActiveMembership(Long userId) {
        List<MembershipRow> rows = jdbcTemplate.query("""
                SELECT user_id, plan_code, status, start_at, end_at,
                       CASE WHEN status = 'ACTIVE' AND NOW() BETWEEN start_at AND end_at THEN 1 ELSE 0 END AS active
                FROM user_membership
                WHERE user_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new MembershipRow(
                rs.getLong("user_id"),
                rs.getString("plan_code"),
                rs.getString("status"),
                rs.getString("start_at"),
                rs.getString("end_at"),
                rs.getInt("active") == 1
        ), userId);

        if (rows.isEmpty()) {
            return null;
        }

        MembershipRow row = rows.get(0);
        if (!row.active()) {
            return null;
        }
        return row;
    }

    private MemberPlanView plan(String planCode) {
        List<MemberPlanView> rows = jdbcTemplate.query("""
                SELECT id, plan_code, plan_name, description, price_cent, duration_days, level_no, max_upload_mb
                FROM member_plan
                WHERE plan_code = ?
                  AND status = 1
                LIMIT 1
                """, (rs, rowNum) -> new MemberPlanView(
                rs.getLong("id"),
                rs.getString("plan_code"),
                rs.getString("plan_name"),
                rs.getString("description"),
                rs.getLong("price_cent"),
                rs.getInt("duration_days"),
                rs.getInt("level_no"),
                rs.getLong("max_upload_mb")
        ), planCode);

        if (rows.isEmpty()) {
            throw new BizException("会员套餐不存在：" + planCode);
        }
        return rows.get(0);
    }

    private List<MemberBenefitView> benefitsByPlan(String planCode) {
        return jdbcTemplate.query("""
                SELECT plan_code, benefit_code, benefit_name, benefit_value
                FROM member_benefit
                WHERE plan_code = ?
                  AND status = 1
                ORDER BY id ASC
                """, (rs, rowNum) -> new MemberBenefitView(
                rs.getString("plan_code"),
                rs.getString("benefit_code"),
                rs.getString("benefit_name"),
                rs.getString("benefit_value")
        ), planCode);
    }

    private Long normalizeUserId(Long userId) {
        return userId == null ? 1L : userId;
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value.replace(" ", "T"));
    }

    private record MembershipRow(
            Long userId,
            String planCode,
            String status,
            String startAt,
            String endAt,
            Boolean active
    ) {
    }
}
