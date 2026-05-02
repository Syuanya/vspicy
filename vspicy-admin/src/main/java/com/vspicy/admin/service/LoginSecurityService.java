package com.vspicy.admin.service;

import com.vspicy.admin.dto.*;
import com.vspicy.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LoginSecurityService {
    private final JdbcTemplate jdbcTemplate;

    public LoginSecurityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginSecurityOverviewView overview(int days) {
        int safeDays = days <= 0 ? 7 : Math.min(days, 90);
        Long todaySuccess = jdbcTemplate.queryForObject("select count(*) from sys_login_log where status='SUCCESS' and date(created_at)=curdate()", Long.class);
        Long todayFailed = jdbcTemplate.queryForObject("select count(*) from sys_login_log where status='FAILED' and date(created_at)=curdate()", Long.class);
        long success = todaySuccess == null ? 0 : todaySuccess;
        long failed = todayFailed == null ? 0 : todayFailed;
        long total = success + failed;
        Long online = jdbcTemplate.queryForObject("select count(*) from sys_online_session where status='ONLINE'", Long.class);
        Long kicked = jdbcTemplate.queryForObject("select count(*) from sys_online_session where status='KICKED'", Long.class);
        Long abnormalIp = jdbcTemplate.queryForObject("select count(*) from (select ip from sys_login_log where created_at >= date_sub(now(), interval 1 day) group by ip having sum(status='FAILED') >= 5) t", Long.class);
        return new LoginSecurityOverviewView(
                total,
                success,
                failed,
                total == 0 ? 0 : Math.round(success * 10000.0 / total) / 100.0,
                online == null ? 0 : online,
                kicked == null ? 0 : kicked,
                abnormalIp == null ? 0 : abnormalIp,
                stat("select status name, count(*) value from sys_login_log group by status"),
                stat("select login_type name, count(*) value from sys_login_log group by login_type"),
                daily(safeDays)
        );
    }

    public List<LoginLogItem> loginLogs(Long userId, String status, String keyword, int limit) {
        StringBuilder sql = new StringBuilder("select l.id,l.user_id,u.username,u.nickname,l.login_type,l.ip,l.location,l.user_agent,l.device,l.status,l.fail_reason,l.created_at from sys_login_log l left join sys_user u on u.id=l.user_id where 1=1");
        List<Object> args = new ArrayList<>();
        if (userId != null) {
            sql.append(" and l.user_id=?");
            args.add(userId);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and l.status=?");
            args.add(status.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (u.username like ? or u.nickname like ? or l.ip like ? or l.user_agent like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        sql.append(" order by l.created_at desc limit ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new LoginLogItem(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("login_type"),
                rs.getString("ip"),
                rs.getString("location"),
                rs.getString("user_agent"),
                rs.getString("device"),
                rs.getString("status"),
                rs.getString("fail_reason"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), args.toArray());
    }

    public List<OnlineSessionItem> sessions(Long userId, String status, String keyword, int limit) {
        StringBuilder sql = new StringBuilder("select s.id,s.user_id,u.username,u.nickname,s.token_id,s.ip,s.device,s.status,s.login_at,s.last_active_at,s.expire_at from sys_online_session s left join sys_user u on u.id=s.user_id where 1=1");
        List<Object> args = new ArrayList<>();
        if (userId != null) {
            sql.append(" and s.user_id=?");
            args.add(userId);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and s.status=?");
            args.add(status.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (u.username like ? or u.nickname like ? or s.ip like ? or s.device like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        sql.append(" order by s.last_active_at desc limit ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OnlineSessionItem(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("token_id"),
                rs.getString("ip"),
                rs.getString("device"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("login_at")),
                toLocalDateTime(rs.getTimestamp("last_active_at")),
                toLocalDateTime(rs.getTimestamp("expire_at"))
        ), args.toArray());
    }

    public void kickSession(Long id, SessionKickCommand command) {
        int updated = jdbcTemplate.update("update sys_online_session set status='KICKED', kick_reason=?, updated_at=now() where id=? and status='ONLINE'",
                command == null || !StringUtils.hasText(command.reason()) ? "管理员强制下线" : command.reason().trim(), id);
        if (updated == 0) {
            throw new BizException(404, "在线会话不存在或已失效");
        }
    }

    public int cleanupExpired() {
        return jdbcTemplate.update("update sys_online_session set status='EXPIRED', updated_at=now() where status='ONLINE' and expire_at < now()");
    }

    private List<LoginSecurityStatItem> stat(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new LoginSecurityStatItem(rs.getString("name"), rs.getLong("value")));
    }

    private List<LoginSecurityDailyItem> daily(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select date(created_at) d, sum(status='SUCCESS') success_count, sum(status='FAILED') failed_count from sys_login_log where created_at >= ? group by date(created_at) order by d", start);
        List<LoginSecurityDailyItem> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(new LoginSecurityDailyItem(String.valueOf(row.get("d")), number(row.get("success_count")), number(row.get("failed_count"))));
        }
        return list;
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
