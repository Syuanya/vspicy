package com.vspicy.admin.service;

import com.vspicy.admin.dto.SupportTicketAssignCommand;
import com.vspicy.admin.dto.SupportTicketCommand;
import com.vspicy.admin.dto.SupportTicketMetricItem;
import com.vspicy.admin.dto.SupportTicketOverviewView;
import com.vspicy.admin.dto.SupportTicketReplyCommand;
import com.vspicy.admin.dto.SupportTicketReplyView;
import com.vspicy.admin.dto.SupportTicketView;
import com.vspicy.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SupportTicketService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TICKET_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> CATEGORIES = Set.of("ACCOUNT", "VIDEO", "PAYMENT", "CONTENT", "SYSTEM", "OTHER");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED");
    private static final Set<String> REPLY_TYPES = Set.of("PUBLIC", "INTERNAL", "SYSTEM");

    private final JdbcTemplate jdbcTemplate;

    public SupportTicketService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SupportTicketOverviewView overview() {
        List<SupportTicketView> items = list(null, null, null, null, null, 1000);
        LocalDate today = LocalDate.now();

        long total = items.size();
        long open = countByStatus(items, "OPEN");
        long processing = countByStatus(items, "PROCESSING");
        long resolved = countByStatus(items, "RESOLVED");
        long closed = countByStatus(items, "CLOSED");
        long urgent = items.stream().filter(item -> "URGENT".equalsIgnoreCase(item.priority())).count();
        long todayCreated = items.stream()
                .filter(item -> parseTime(item.createdAt()) != null)
                .filter(item -> today.equals(parseTime(item.createdAt()).toLocalDate()))
                .count();
        long unassigned = items.stream().filter(item -> item.assigneeId() == null).count();

        return new SupportTicketOverviewView(
                total,
                open,
                processing,
                resolved,
                closed,
                urgent,
                todayCreated,
                unassigned,
                distribution(items, "category"),
                distribution(items, "priority"),
                distribution(items, "status")
        );
    }

    public List<SupportTicketView> list(
            String status,
            String category,
            String priority,
            Long assigneeId,
            String keyword,
            Integer limit
    ) {
        int safeLimit = limit == null || limit <= 0 || limit > 1000 ? 100 : limit;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 ");

        if (hasText(status)) {
            where.append(" AND status = ? ");
            params.add(normalizeStatus(status));
        }
        if (hasText(category)) {
            where.append(" AND category = ? ");
            params.add(normalizeCategory(category));
        }
        if (hasText(priority)) {
            where.append(" AND priority = ? ");
            params.add(normalizePriority(priority));
        }
        if (assigneeId != null) {
            where.append(" AND assignee_id = ? ");
            params.add(assigneeId);
        }
        if (hasText(keyword)) {
            where.append("""
                    AND (
                      ticket_no LIKE ?
                      OR title LIKE ?
                      OR content LIKE ?
                      OR submitter_name LIKE ?
                      OR contact LIKE ?
                      OR tags LIKE ?
                    )
                    """);
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
            params.add(value);
            params.add(value);
            params.add(value);
            params.add(value);
        }

        params.add(safeLimit);
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_support_ticket
                """ + where + """
                ORDER BY
                  CASE priority
                    WHEN 'URGENT' THEN 1
                    WHEN 'HIGH' THEN 2
                    WHEN 'NORMAL' THEN 3
                    ELSE 4
                  END,
                  COALESCE(last_reply_at, updated_at, created_at) DESC,
                  id DESC
                LIMIT ?
                """, (rs, rowNum) -> toView(rs, false), params.toArray());
    }

    public SupportTicketView get(Long id) {
        return toView(mustGet(id), true);
    }

    @Transactional
    public SupportTicketView create(SupportTicketCommand command, Long operatorId, String operatorName) {
        validate(command);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String ticketNo = generateTicketNo();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO sys_support_ticket(
                      ticket_no, title, content, category, priority, status,
                      submitter_id, submitter_name, contact,
                      assignee_id, assignee_name,
                      tags, source, remark,
                      created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, ticketNo);
            ps.setString(2, command.title().trim());
            ps.setString(3, command.content().trim());
            ps.setString(4, normalizeCategory(command.category()));
            ps.setString(5, normalizePriority(command.priority()));
            setLong(ps, 6, command.submitterId());
            ps.setString(7, defaultString(command.submitterName(), operatorName));
            ps.setString(8, trimToNull(command.contact()));
            setLong(ps, 9, command.assigneeId());
            ps.setString(10, trimToNull(command.assigneeName()));
            ps.setString(11, trimToNull(command.tags()));
            ps.setString(12, defaultString(command.source(), "ADMIN"));
            ps.setString(13, trimToNull(command.remark()));
            setLong(ps, 14, operatorId);
            setLong(ps, 15, operatorId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("工单创建失败，未获取到 ID");
        }

        addSystemReply(key.longValue(), "工单已创建", operatorId, operatorName);
        return get(key.longValue());
    }

    @Transactional
    public SupportTicketView update(Long id, SupportTicketCommand command, Long operatorId, String operatorName) {
        TicketRow row = mustGet(id);
        if ("CLOSED".equalsIgnoreCase(row.status())) {
            throw new BizException("已关闭工单不能编辑，请先重新打开");
        }
        validate(command);

        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET title = ?,
                    content = ?,
                    category = ?,
                    priority = ?,
                    submitter_id = ?,
                    submitter_name = ?,
                    contact = ?,
                    assignee_id = ?,
                    assignee_name = ?,
                    tags = ?,
                    source = ?,
                    remark = ?,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """,
                command.title().trim(),
                command.content().trim(),
                normalizeCategory(command.category()),
                normalizePriority(command.priority()),
                command.submitterId(),
                defaultString(command.submitterName(), operatorName),
                trimToNull(command.contact()),
                command.assigneeId(),
                trimToNull(command.assigneeName()),
                trimToNull(command.tags()),
                defaultString(command.source(), "ADMIN"),
                trimToNull(command.remark()),
                operatorId,
                id
        );
        addSystemReply(id, "工单信息已更新", operatorId, operatorName);
        return get(id);
    }

    @Transactional
    public SupportTicketView assign(Long id, SupportTicketAssignCommand command, Long operatorId, String operatorName) {
        TicketRow row = mustGet(id);
        if ("CLOSED".equalsIgnoreCase(row.status())) {
            throw new BizException("已关闭工单不能分派");
        }
        if (command == null || command.assigneeId() == null) {
            throw new BizException("请选择处理人");
        }
        String assigneeName = hasText(command.assigneeName()) ? command.assigneeName().trim() : "用户#" + command.assigneeId();

        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET assignee_id = ?,
                    assignee_name = ?,
                    status = CASE WHEN status = 'OPEN' THEN 'PROCESSING' ELSE status END,
                    remark = COALESCE(?, remark),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, command.assigneeId(), assigneeName, trimToNull(command.remark()), operatorId, id);

        addSystemReply(id, "工单已分派给 " + assigneeName, operatorId, operatorName);
        return get(id);
    }

    @Transactional
    public SupportTicketView reply(Long id, SupportTicketReplyCommand command, Long operatorId, String operatorName) {
        TicketRow row = mustGet(id);
        if ("CLOSED".equalsIgnoreCase(row.status())) {
            throw new BizException("已关闭工单不能回复，请先重新打开");
        }
        if (command == null || !hasText(command.content())) {
            throw new BizException("回复内容不能为空");
        }
        String replyType = normalizeReplyType(command.replyType());
        boolean visibleToUser = command.visibleToUser() == null ? !"INTERNAL".equals(replyType) : Boolean.TRUE.equals(command.visibleToUser());

        jdbcTemplate.update("""
                INSERT INTO sys_support_ticket_reply(
                  ticket_id, content, reply_type, visible_to_user, operator_id, operator_name
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, id, command.content().trim(), replyType, visibleToUser ? 1 : 0, operatorId, operatorName);

        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET status = CASE WHEN status = 'OPEN' THEN 'PROCESSING' ELSE status END,
                    last_reply_at = NOW(),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, operatorId, id);

        return get(id);
    }

    @Transactional
    public SupportTicketView resolve(Long id, Long operatorId, String operatorName) {
        TicketRow row = mustGet(id);
        if ("CLOSED".equalsIgnoreCase(row.status())) {
            throw new BizException("已关闭工单不能标记解决");
        }
        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET status = 'RESOLVED',
                    resolved_by = ?,
                    resolved_at = NOW(),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, operatorId, operatorId, id);
        addSystemReply(id, "工单已标记为已解决", operatorId, operatorName);
        return get(id);
    }

    @Transactional
    public SupportTicketView close(Long id, Long operatorId, String operatorName) {
        mustGet(id);
        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET status = 'CLOSED',
                    closed_at = NOW(),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, operatorId, id);
        addSystemReply(id, "工单已关闭", operatorId, operatorName);
        return get(id);
    }

    @Transactional
    public SupportTicketView reopen(Long id, Long operatorId, String operatorName) {
        TicketRow row = mustGet(id);
        if (!"CLOSED".equalsIgnoreCase(row.status()) && !"RESOLVED".equalsIgnoreCase(row.status())) {
            throw new BizException("只有已解决或已关闭工单可以重新打开");
        }
        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET status = 'PROCESSING',
                    closed_at = NULL,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, operatorId, id);
        addSystemReply(id, "工单已重新打开", operatorId, operatorName);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        mustGet(id);
        jdbcTemplate.update("UPDATE sys_support_ticket SET deleted = 1, updated_at = NOW() WHERE id = ?", id);
    }

    private void addSystemReply(Long ticketId, String content, Long operatorId, String operatorName) {
        jdbcTemplate.update("""
                INSERT INTO sys_support_ticket_reply(
                  ticket_id, content, reply_type, visible_to_user, operator_id, operator_name
                ) VALUES (?, ?, 'SYSTEM', 0, ?, ?)
                """, ticketId, content, operatorId, operatorName);
        jdbcTemplate.update("""
                UPDATE sys_support_ticket
                SET last_reply_at = NOW(), updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, ticketId);
    }

    private TicketRow mustGet(Long id) {
        if (id == null) {
            throw new BizException("工单 ID 不能为空");
        }
        List<TicketRow> rows = jdbcTemplate.query("""
                SELECT *
                FROM sys_support_ticket
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, (rs, rowNum) -> toRow(rs), id);
        if (rows.isEmpty()) {
            throw new BizException("工单不存在或已删除");
        }
        return rows.get(0);
    }

    private SupportTicketView toView(ResultSet rs, boolean withReplies) throws java.sql.SQLException {
        return toView(toRow(rs), withReplies);
    }

    private SupportTicketView toView(TicketRow row, boolean withReplies) {
        return new SupportTicketView(
                row.id(),
                row.ticketNo(),
                row.title(),
                row.content(),
                row.category(),
                row.priority(),
                row.status(),
                row.submitterId(),
                row.submitterName(),
                row.contact(),
                row.assigneeId(),
                row.assigneeName(),
                row.resolvedBy(),
                format(row.resolvedAt()),
                format(row.closedAt()),
                format(row.lastReplyAt()),
                row.tags(),
                row.source(),
                row.remark(),
                format(row.createdAt()),
                format(row.updatedAt()),
                withReplies ? replies(row.id()) : List.of()
        );
    }

    private TicketRow toRow(ResultSet rs) throws java.sql.SQLException {
        return new TicketRow(
                rs.getLong("id"),
                rs.getString("ticket_no"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("category"),
                rs.getString("priority"),
                rs.getString("status"),
                getNullableLong(rs, "submitter_id"),
                rs.getString("submitter_name"),
                rs.getString("contact"),
                getNullableLong(rs, "assignee_id"),
                rs.getString("assignee_name"),
                getNullableLong(rs, "resolved_by"),
                toLocalDateTime(rs.getTimestamp("resolved_at")),
                toLocalDateTime(rs.getTimestamp("closed_at")),
                toLocalDateTime(rs.getTimestamp("last_reply_at")),
                rs.getString("tags"),
                rs.getString("source"),
                rs.getString("remark"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private List<SupportTicketReplyView> replies(Long ticketId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_support_ticket_reply
                WHERE ticket_id = ?
                ORDER BY id ASC
                """, (rs, rowNum) -> new SupportTicketReplyView(
                rs.getLong("id"),
                rs.getLong("ticket_id"),
                rs.getString("content"),
                rs.getString("reply_type"),
                rs.getInt("visible_to_user") == 1,
                getNullableLong(rs, "operator_id"),
                rs.getString("operator_name"),
                format(toLocalDateTime(rs.getTimestamp("created_at")))
        ), ticketId);
    }

    private List<SupportTicketMetricItem> distribution(List<SupportTicketView> items, String field) {
        List<SupportTicketMetricItem> result = new ArrayList<>();
        if ("category".equals(field)) {
            for (String category : CATEGORIES) {
                result.add(new SupportTicketMetricItem(category, items.stream().filter(item -> category.equalsIgnoreCase(item.category())).count()));
            }
        } else if ("priority".equals(field)) {
            for (String priority : PRIORITIES) {
                result.add(new SupportTicketMetricItem(priority, items.stream().filter(item -> priority.equalsIgnoreCase(item.priority())).count()));
            }
        } else if ("status".equals(field)) {
            for (String status : STATUSES) {
                result.add(new SupportTicketMetricItem(status, items.stream().filter(item -> status.equalsIgnoreCase(item.status())).count()));
            }
        }
        return result;
    }

    private long countByStatus(List<SupportTicketView> items, String status) {
        return items.stream().filter(item -> status.equalsIgnoreCase(item.status())).count();
    }

    private void validate(SupportTicketCommand command) {
        if (command == null) {
            throw new BizException("工单参数不能为空");
        }
        if (!hasText(command.title())) {
            throw new BizException("工单标题不能为空");
        }
        if (command.title().trim().length() > 120) {
            throw new BizException("工单标题不能超过 120 个字符");
        }
        if (!hasText(command.content())) {
            throw new BizException("工单内容不能为空");
        }
        if (command.content().trim().length() > 5000) {
            throw new BizException("工单内容不能超过 5000 个字符");
        }
        normalizeCategory(command.category());
        normalizePriority(command.priority());
    }

    private String generateTicketNo() {
        return "TCK" + LocalDateTime.now().format(TICKET_NO_TIME) + String.format("%04d", Math.abs((int) (System.nanoTime() % 10000)));
    }

    private String normalizeCategory(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "OTHER";
        if (!CATEGORIES.contains(normalized)) {
            throw new BizException("工单分类不合法：" + value);
        }
        return normalized;
    }

    private String normalizePriority(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "NORMAL";
        if (!PRIORITIES.contains(normalized)) {
            throw new BizException("工单优先级不合法：" + value);
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "OPEN";
        if (!STATUSES.contains(normalized)) {
            throw new BizException("工单状态不合法：" + value);
        }
        return normalized;
    }

    private String normalizeReplyType(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "PUBLIC";
        if (!REPLY_TYPES.contains(normalized)) {
            throw new BizException("回复类型不合法：" + value);
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long getNullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private LocalDateTime parseTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        return LocalDateTime.parse(normalized, DATE_TIME);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private record TicketRow(
            Long id,
            String ticketNo,
            String title,
            String content,
            String category,
            String priority,
            String status,
            Long submitterId,
            String submitterName,
            String contact,
            Long assigneeId,
            String assigneeName,
            Long resolvedBy,
            LocalDateTime resolvedAt,
            LocalDateTime closedAt,
            LocalDateTime lastReplyAt,
            String tags,
            String source,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
