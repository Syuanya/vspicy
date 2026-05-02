package com.vspicy.notification.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.notification.dto.AnnouncementCommand;
import com.vspicy.notification.dto.AnnouncementMetricItem;
import com.vspicy.notification.dto.AnnouncementOverviewView;
import com.vspicy.notification.dto.AnnouncementPublishCommand;
import com.vspicy.notification.dto.AnnouncementView;
import com.vspicy.notification.dto.NotificationCreateCommand;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationAnnouncementService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> CATEGORIES = Set.of("SYSTEM", "OPERATION", "MAINTENANCE", "ACTIVITY", "SECURITY");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE", "ARCHIVED");

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    public NotificationAnnouncementService(JdbcTemplate jdbcTemplate, NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    public List<AnnouncementView> list(
            String status,
            String category,
            String priority,
            Boolean pinned,
            Boolean onlyEffective,
            String keyword,
            Integer limit
    ) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

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
        if (pinned != null) {
            where.append(" AND pinned = ? ");
            params.add(Boolean.TRUE.equals(pinned) ? 1 : 0);
        }
        if (Boolean.TRUE.equals(onlyEffective)) {
            where.append("""
                     AND status = 'PUBLISHED'
                     AND (publish_start_at IS NULL OR publish_start_at <= NOW())
                     AND (publish_end_at IS NULL OR publish_end_at >= NOW())
                    """);
        }
        if (hasText(keyword)) {
            where.append(" AND (title LIKE ? OR content LIKE ? OR remark LIKE ?) ");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
            params.add(value);
        }

        params.add(safeLimit);
        return jdbcTemplate.query("""
                SELECT *
                FROM notification_announcement
                """ + where + """
                ORDER BY pinned DESC, COALESCE(published_at, updated_at, created_at) DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> toView(rs), params.toArray());
    }

    public AnnouncementOverviewView overview() {
        List<AnnouncementView> items = list(null, null, null, null, false, null, 500);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        long total = items.size();
        long draft = countByStatus(items, "DRAFT");
        long published = countByStatus(items, "PUBLISHED");
        long offline = countByStatus(items, "OFFLINE");
        long archived = countByStatus(items, "ARCHIVED");
        long pinned = items.stream().filter(item -> Boolean.TRUE.equals(item.pinned())).count();
        long effective = items.stream().filter(item -> isEffective(item, now)).count();
        long expired = items.stream().filter(item -> isExpired(item, now)).count();
        long todayPublished = items.stream()
                .filter(item -> parseTime(item.publishedAt()) != null)
                .filter(item -> today.equals(parseTime(item.publishedAt()).toLocalDate()))
                .count();

        return new AnnouncementOverviewView(
                total,
                draft,
                published,
                offline,
                archived,
                pinned,
                effective,
                expired,
                todayPublished,
                distribution(items, "category"),
                distribution(items, "status")
        );
    }

    public AnnouncementView get(Long id) {
        return toView(mustGet(id));
    }

    @Transactional
    public AnnouncementView create(AnnouncementCommand command, Long operatorId) {
        validate(command);
        LocalDateTime startAt = parseTime(command.publishStartAt());
        LocalDateTime endAt = parseTime(command.publishEndAt());
        validateWindow(startAt, endAt);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO notification_announcement(
                      title, content, category, priority, status, pinned,
                      publish_start_at, publish_end_at, created_by, updated_by, remark
                    ) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, command.title().trim());
            ps.setString(2, command.content().trim());
            ps.setString(3, normalizeCategory(command.category()));
            ps.setString(4, normalizePriority(command.priority()));
            ps.setInt(5, Boolean.TRUE.equals(command.pinned()) ? 1 : 0);
            setDateTime(ps, 6, startAt);
            setDateTime(ps, 7, endAt);
            setLong(ps, 8, operatorId);
            setLong(ps, 9, operatorId);
            ps.setString(10, trimToNull(command.remark()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("公告创建失败，未获取到 ID");
        }
        return get(key.longValue());
    }

    @Transactional
    public AnnouncementView update(Long id, AnnouncementCommand command, Long operatorId) {
        AnnouncementRow row = mustGet(id);
        if ("ARCHIVED".equalsIgnoreCase(row.status())) {
            throw new BizException("已归档公告不能修改");
        }
        validate(command);
        LocalDateTime startAt = parseTime(command.publishStartAt());
        LocalDateTime endAt = parseTime(command.publishEndAt());
        validateWindow(startAt, endAt);

        jdbcTemplate.update("""
                UPDATE notification_announcement
                SET title = ?,
                    content = ?,
                    category = ?,
                    priority = ?,
                    pinned = ?,
                    publish_start_at = ?,
                    publish_end_at = ?,
                    updated_by = ?,
                    remark = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                command.title().trim(),
                command.content().trim(),
                normalizeCategory(command.category()),
                normalizePriority(command.priority()),
                Boolean.TRUE.equals(command.pinned()) ? 1 : 0,
                toTimestamp(startAt),
                toTimestamp(endAt),
                operatorId,
                trimToNull(command.remark()),
                id
        );

        AnnouncementRow updated = mustGet(id);
        if (updated.publishedMessageId() != null) {
            jdbcTemplate.update("""
                    UPDATE notification_message
                    SET title = ?, content = ?, notification_type = 'ANNOUNCEMENT', biz_type = 'ANNOUNCEMENT',
                        biz_id = ?, priority = ?
                    WHERE id = ?
                    """, updated.title(), updated.content(), updated.id(), updated.priority(), updated.publishedMessageId());
        }
        return toView(updated);
    }

    @Transactional
    public AnnouncementView publish(Long id, AnnouncementPublishCommand command, Long operatorId) {
        AnnouncementRow row = mustGet(id);
        if ("ARCHIVED".equalsIgnoreCase(row.status())) {
            throw new BizException("已归档公告不能发布");
        }
        LocalDateTime startAt = command == null || !hasText(command.publishStartAt()) ? row.publishStartAt() : parseTime(command.publishStartAt());
        LocalDateTime endAt = command == null || !hasText(command.publishEndAt()) ? row.publishEndAt() : parseTime(command.publishEndAt());
        validateWindow(startAt, endAt);
        boolean pinned = command == null || command.pinned() == null ? row.pinned() : Boolean.TRUE.equals(command.pinned());

        Long messageId = row.publishedMessageId();
        if (messageId == null) {
            messageId = notificationService.publishSystem(
                    new NotificationCreateCommand(
                            row.title(),
                            row.content(),
                            "ANNOUNCEMENT",
                            "ANNOUNCEMENT",
                            row.id(),
                            row.priority(),
                            null
                    ),
                    operatorId
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE notification_message
                    SET title = ?, content = ?, notification_type = 'ANNOUNCEMENT', biz_type = 'ANNOUNCEMENT',
                        biz_id = ?, priority = ?, publish_scope = 'ALL', status = 'PUBLISHED'
                    WHERE id = ?
                    """, row.title(), row.content(), row.id(), row.priority(), messageId);
        }

        jdbcTemplate.update("""
                UPDATE notification_announcement
                SET status = 'PUBLISHED',
                    pinned = ?,
                    publish_start_at = ?,
                    publish_end_at = ?,
                    published_message_id = ?,
                    published_by = ?,
                    published_at = COALESCE(published_at, NOW()),
                    offline_at = NULL,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                pinned ? 1 : 0,
                toTimestamp(startAt),
                toTimestamp(endAt),
                messageId,
                operatorId,
                operatorId,
                id
        );
        return get(id);
    }

    @Transactional
    public AnnouncementView offline(Long id, Long operatorId) {
        AnnouncementRow row = mustGet(id);
        if (!"PUBLISHED".equalsIgnoreCase(row.status())) {
            throw new BizException("只有已发布公告可以下线");
        }
        jdbcTemplate.update("""
                UPDATE notification_announcement
                SET status = 'OFFLINE', offline_at = NOW(), updated_by = ?, updated_at = NOW()
                WHERE id = ?
                """, operatorId, id);
        if (row.publishedMessageId() != null) {
            jdbcTemplate.update("UPDATE notification_message SET status = 'OFFLINE' WHERE id = ?", row.publishedMessageId());
        }
        return get(id);
    }

    @Transactional
    public AnnouncementView archive(Long id, Long operatorId) {
        AnnouncementRow row = mustGet(id);
        if ("ARCHIVED".equalsIgnoreCase(row.status())) {
            return toView(row);
        }
        jdbcTemplate.update("""
                UPDATE notification_announcement
                SET status = 'ARCHIVED', offline_at = COALESCE(offline_at, NOW()), updated_by = ?, updated_at = NOW()
                WHERE id = ?
                """, operatorId, id);
        if (row.publishedMessageId() != null) {
            jdbcTemplate.update("UPDATE notification_message SET status = 'ARCHIVED' WHERE id = ?", row.publishedMessageId());
        }
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        AnnouncementRow row = mustGet(id);
        if ("PUBLISHED".equalsIgnoreCase(row.status())) {
            throw new BizException("已发布公告请先下线后再删除");
        }
        jdbcTemplate.update("DELETE FROM notification_announcement WHERE id = ?", id);
        if (row.publishedMessageId() != null) {
            jdbcTemplate.update("UPDATE notification_message SET status = 'DELETED' WHERE id = ?", row.publishedMessageId());
        }
    }

    private AnnouncementRow mustGet(Long id) {
        if (id == null || id <= 0) {
            throw new BizException("公告 ID 不能为空");
        }
        List<AnnouncementRow> rows = jdbcTemplate.query(
                "SELECT * FROM notification_announcement WHERE id = ?",
                (rs, rowNum) -> toRow(rs),
                id
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "公告不存在");
        }
        return rows.get(0);
    }

    private void validate(AnnouncementCommand command) {
        if (command == null) {
            throw new BizException("公告内容不能为空");
        }
        if (!hasText(command.title())) {
            throw new BizException("公告标题不能为空");
        }
        if (command.title().trim().length() > 120) {
            throw new BizException("公告标题不能超过 120 个字符");
        }
        if (!hasText(command.content())) {
            throw new BizException("公告正文不能为空");
        }
        if (command.content().trim().length() > 10000) {
            throw new BizException("公告正文不能超过 10000 个字符");
        }
    }

    private void validateWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new BizException("结束时间不能早于开始时间");
        }
    }

    private boolean isEffective(AnnouncementView item, LocalDateTime now) {
        if (!"PUBLISHED".equalsIgnoreCase(value(item.status()))) {
            return false;
        }
        LocalDateTime start = parseTime(item.publishStartAt());
        LocalDateTime end = parseTime(item.publishEndAt());
        return (start == null || !start.isAfter(now)) && (end == null || !end.isBefore(now));
    }

    private boolean isExpired(AnnouncementView item, LocalDateTime now) {
        LocalDateTime end = parseTime(item.publishEndAt());
        return end != null && end.isBefore(now);
    }

    private long countByStatus(List<AnnouncementView> items, String status) {
        return items.stream().filter(item -> status.equalsIgnoreCase(value(item.status()))).count();
    }

    private List<AnnouncementMetricItem> distribution(List<AnnouncementView> items, String type) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (AnnouncementView item : items) {
            String key = "category".equals(type) ? value(item.category(), "UNKNOWN") : value(item.status(), "UNKNOWN");
            result.put(key, result.getOrDefault(key, 0L) + 1L);
        }
        return result.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> new AnnouncementMetricItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private AnnouncementView toView(ResultSet rs) throws java.sql.SQLException {
        return toView(toRow(rs));
    }

    private AnnouncementView toView(AnnouncementRow row) {
        return new AnnouncementView(
                row.id(),
                row.title(),
                row.content(),
                row.category(),
                row.priority(),
                row.status(),
                row.pinned(),
                format(row.publishStartAt()),
                format(row.publishEndAt()),
                row.publishedMessageId(),
                row.createdBy(),
                row.updatedBy(),
                row.publishedBy(),
                format(row.publishedAt()),
                format(row.offlineAt()),
                row.remark(),
                format(row.createdAt()),
                format(row.updatedAt())
        );
    }

    private AnnouncementRow toRow(ResultSet rs) throws java.sql.SQLException {
        return new AnnouncementRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("category"),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getInt("pinned") == 1,
                getDateTime(rs, "publish_start_at"),
                getDateTime(rs, "publish_end_at"),
                getNullableLong(rs.getObject("published_message_id")),
                getNullableLong(rs.getObject("created_by")),
                getNullableLong(rs.getObject("updated_by")),
                getNullableLong(rs.getObject("published_by")),
                getDateTime(rs, "published_at"),
                getDateTime(rs, "offline_at"),
                rs.getString("remark"),
                getDateTime(rs, "created_at"),
                getDateTime(rs, "updated_at")
        );
    }

    private String normalizeCategory(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "SYSTEM";
        return CATEGORIES.contains(normalized) ? normalized : "SYSTEM";
    }

    private String normalizePriority(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "NORMAL";
        return PRIORITIES.contains(normalized) ? normalized : "NORMAL";
    }

    private String normalizeStatus(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "DRAFT";
        return STATUSES.contains(normalized) ? normalized : "DRAFT";
    }

    private LocalDateTime parseTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() == 16) {
            text = text + ":00";
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private LocalDateTime getDateTime(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private void setDateTime(PreparedStatement ps, int index, LocalDateTime value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long getNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private record AnnouncementRow(
            Long id,
            String title,
            String content,
            String category,
            String priority,
            String status,
            Boolean pinned,
            LocalDateTime publishStartAt,
            LocalDateTime publishEndAt,
            Long publishedMessageId,
            Long createdBy,
            Long updatedBy,
            Long publishedBy,
            LocalDateTime publishedAt,
            LocalDateTime offlineAt,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
