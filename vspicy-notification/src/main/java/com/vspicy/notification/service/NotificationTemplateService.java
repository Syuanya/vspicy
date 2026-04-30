package com.vspicy.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.notification.dto.NotificationCreateCommand;
import com.vspicy.notification.dto.NotificationTemplateCommand;
import com.vspicy.notification.dto.NotificationTemplateCopyCommand;
import com.vspicy.notification.dto.NotificationTemplatePreviewView;
import com.vspicy.notification.dto.NotificationTemplatePublishCheckView;
import com.vspicy.notification.dto.NotificationTemplatePublishCommand;
import com.vspicy.notification.dto.NotificationTemplatePublishLogView;
import com.vspicy.notification.dto.NotificationTemplateValidationView;
import com.vspicy.notification.dto.NotificationTemplateView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationTemplateService {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}|\\$\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}");

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationTemplateService(
            JdbcTemplate jdbcTemplate,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public List<NotificationTemplateView> list(String keyword, Boolean enabled, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 300 ? 100 : limit;
        StringBuilder sql = new StringBuilder("""
                SELECT id, template_code, template_name, title_template, content_template,
                       notification_type, biz_type, priority, enabled, remark,
                       created_by, updated_by, created_at, updated_at
                FROM notification_template
                WHERE deleted = 0
                """);

        ArrayList<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (template_code LIKE ? OR template_name LIKE ? OR title_template LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ? ");
            params.add(Boolean.TRUE.equals(enabled) ? 1 : 0);
        }
        sql.append(" ORDER BY id DESC LIMIT ? ");
        params.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTemplate(rs), params.toArray());
    }

    public NotificationTemplateView detail(Long id) {
        if (id == null) {
            throw new BizException("模板 ID 不能为空");
        }
        List<NotificationTemplateView> list = jdbcTemplate.query("""
                SELECT id, template_code, template_name, title_template, content_template,
                       notification_type, biz_type, priority, enabled, remark,
                       created_by, updated_by, created_at, updated_at
                FROM notification_template
                WHERE id = ? AND deleted = 0
                """, (rs, rowNum) -> mapTemplate(rs), id);
        if (list.isEmpty()) {
            throw new BizException(404, "通知模板不存在");
        }
        return list.get(0);
    }

    @Transactional
    public Long create(NotificationTemplateCommand command, Long operatorId) {
        validate(command);
        String code = normalizeCode(command.templateCode());
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO notification_template(
                          template_code, template_name, title_template, content_template,
                          notification_type, biz_type, priority, enabled, remark,
                          created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                bindTemplate(ps, command, code, operatorId, operatorId);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new BizException("模板编码已存在：" + code);
        }

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("通知模板创建失败，未获取到 ID");
        }
        return key.longValue();
    }

    @Transactional
    public NotificationTemplateView update(Long id, NotificationTemplateCommand command, Long operatorId) {
        if (id == null) {
            throw new BizException("模板 ID 不能为空");
        }
        validate(command);
        String code = normalizeCode(command.templateCode());

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE notification_template
                    SET template_code = ?,
                        template_name = ?,
                        title_template = ?,
                        content_template = ?,
                        notification_type = ?,
                        biz_type = ?,
                        priority = ?,
                        enabled = ?,
                        remark = ?,
                        updated_by = ?,
                        updated_at = NOW()
                    WHERE id = ? AND deleted = 0
                    """,
                    code,
                    command.templateName().trim(),
                    command.titleTemplate().trim(),
                    command.contentTemplate().trim(),
                    normalizeUpper(command.notificationType(), "SYSTEM"),
                    blankToNull(command.bizType()),
                    normalizeUpper(command.priority(), "NORMAL"),
                    Boolean.FALSE.equals(command.enabled()) ? 0 : 1,
                    blankToNull(command.remark()),
                    operatorId,
                    id
            );

            if (updated == 0) {
                throw new BizException(404, "通知模板不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException("模板编码已存在：" + code);
        }

        return detail(id);
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        if (id == null) {
            throw new BizException("模板 ID 不能为空");
        }
        int updated = jdbcTemplate.update("""
                UPDATE notification_template
                SET deleted = 1,
                    enabled = 0,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, operatorId, id);
        if (updated == 0) {
            throw new BizException(404, "通知模板不存在");
        }
    }

    @Transactional
    public Long copy(Long id, NotificationTemplateCopyCommand command, Long operatorId) {
        NotificationTemplateView source = detail(id);
        String code = command != null && command.templateCode() != null && !command.templateCode().isBlank()
                ? normalizeCode(command.templateCode())
                : nextCopyCode(source.templateCode());
        String name = command != null && command.templateName() != null && !command.templateName().isBlank()
                ? command.templateName().trim()
                : source.templateName() + " 副本";
        Boolean enabled = command == null ? Boolean.FALSE : command.enabled();
        NotificationTemplateCommand createCommand = new NotificationTemplateCommand(
                code,
                name,
                source.titleTemplate(),
                source.contentTemplate(),
                source.notificationType(),
                source.bizType(),
                source.priority(),
                Boolean.TRUE.equals(enabled),
                source.remark()
        );
        return create(createCommand, operatorId);
    }

    public NotificationTemplatePreviewView preview(Long id, NotificationTemplatePublishCommand command) {
        NotificationTemplateView template = detail(id);
        return render(template, command);
    }

    public NotificationTemplateValidationView validateVariables(Long id, NotificationTemplatePublishCommand command) {
        NotificationTemplateView template = detail(id);
        return validateVariables(template, extractVariables(command));
    }

    public NotificationTemplatePublishCheckView publishCheck(Long id, NotificationTemplatePublishCommand command) {
        NotificationTemplateView template = detail(id);
        NotificationTemplatePreviewView preview = render(template, command);
        NotificationTemplateValidationView validation = validateVariables(template, extractVariables(command));
        List<Long> receivers = command == null ? null : command.receiverUserIds();
        boolean broadcast = receivers == null || receivers.isEmpty();
        long receiverCount = broadcast ? activeUserCount() : receivers.stream().filter(item -> item != null && item > 0).distinct().count();
        return new NotificationTemplatePublishCheckView(
                template.id(),
                template.templateCode(),
                template.templateName(),
                broadcast ? "ALL" : "SPECIFIED",
                receiverCount,
                preview.title(),
                preview.content(),
                preview.notificationType(),
                preview.bizType(),
                preview.priority(),
                validation
        );
    }

    public Long publish(Long id, NotificationTemplatePublishCommand command, Long operatorId) {
        NotificationTemplateView template = detail(id);
        NotificationTemplatePreviewView preview = render(template, command);
        NotificationTemplateValidationView validation = validateVariables(template, extractVariables(command));
        List<Long> receivers = normalizeReceiverIds(command == null ? null : command.receiverUserIds());
        String receiverMode = receivers.isEmpty() ? "ALL" : "SPECIFIED";
        int receiverCount = "ALL".equals(receiverMode) ? Math.toIntExact(Math.min(activeUserCount(), Integer.MAX_VALUE)) : receivers.size();
        Long logId = insertPublishLog(template, command, preview, receiverMode, receiverCount, operatorId);

        try {
            if (!Boolean.TRUE.equals(template.enabled())) {
                throw new BizException("通知模板已停用，不能发布");
            }
            if (!Boolean.TRUE.equals(validation.valid())) {
                throw new BizException("模板变量缺失：" + String.join(", ", validation.missingVariables()));
            }

            NotificationCreateCommand createCommand = new NotificationCreateCommand(
                    preview.title(),
                    preview.content(),
                    preview.notificationType(),
                    preview.bizType(),
                    command == null ? null : command.bizId(),
                    preview.priority(),
                    receivers.isEmpty() ? null : receivers
            );
            Long messageId = notificationService.publishSystem(createCommand, operatorId);
            markPublishLogSuccess(logId, messageId);
            return messageId;
        } catch (RuntimeException ex) {
            markPublishLogFailed(logId, ex.getMessage());
            throw ex;
        }
    }

    public List<NotificationTemplatePublishLogView> publishLogs(Long templateId, String status, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 300 ? 100 : limit;
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.template_id, t.template_code, t.template_name, l.message_id,
                       l.title, l.content, l.notification_type, l.biz_type, l.biz_id, l.priority,
                       l.receiver_mode, l.receiver_count, l.status, l.error_message,
                       l.variables_json, l.receiver_user_ids_json, l.operator_id,
                       l.created_at, l.updated_at
                FROM notification_template_publish_log l
                LEFT JOIN notification_template t ON t.id = l.template_id
                WHERE 1 = 1
                """);
        ArrayList<Object> params = new ArrayList<>();
        if (templateId != null && templateId > 0) {
            sql.append(" AND l.template_id = ? ");
            params.add(templateId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND l.status = ? ");
            params.add(status.trim().toUpperCase());
        }
        sql.append(" ORDER BY l.id DESC LIMIT ? ");
        params.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapPublishLog(rs), params.toArray());
    }

    public NotificationTemplatePublishLogView publishLogDetail(Long logId) {
        if (logId == null || logId <= 0) {
            throw new BizException("发布记录 ID 不能为空");
        }
        List<NotificationTemplatePublishLogView> logs = jdbcTemplate.query("""
                SELECT l.id, l.template_id, t.template_code, t.template_name, l.message_id,
                       l.title, l.content, l.notification_type, l.biz_type, l.biz_id, l.priority,
                       l.receiver_mode, l.receiver_count, l.status, l.error_message,
                       l.variables_json, l.receiver_user_ids_json, l.operator_id,
                       l.created_at, l.updated_at
                FROM notification_template_publish_log l
                LEFT JOIN notification_template t ON t.id = l.template_id
                WHERE l.id = ?
                """, (rs, rowNum) -> mapPublishLog(rs), logId);
        if (logs.isEmpty()) {
            throw new BizException(404, "模板发布记录不存在");
        }
        return logs.get(0);
    }

    public Long retryPublishLog(Long logId, Long operatorId) {
        NotificationTemplatePublishLogView log = publishLogDetail(logId);
        if (!"FAILED".equalsIgnoreCase(log.status())) {
            throw new BizException("只有失败的发布记录允许重试");
        }
        if (log.templateId() == null) {
            throw new BizException("发布记录缺少模板 ID，不能重试");
        }
        NotificationTemplatePublishCommand command = new NotificationTemplatePublishCommand(
                readReceiverIds(log.receiverUserIdsJson()),
                readVariables(log.variablesJson()),
                log.bizId(),
                log.bizType(),
                log.priority()
        );
        return publish(log.templateId(), command, operatorId);
    }

    private Long insertPublishLog(
            NotificationTemplateView template,
            NotificationTemplatePublishCommand command,
            NotificationTemplatePreviewView preview,
            String receiverMode,
            int receiverCount,
            Long operatorId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO notification_template_publish_log(
                      template_id, title, content, notification_type, biz_type, biz_id, priority,
                      receiver_mode, receiver_count, variables_json, receiver_user_ids_json,
                      operator_id, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, template.id());
            ps.setString(2, preview.title());
            ps.setString(3, preview.content());
            ps.setString(4, preview.notificationType());
            ps.setString(5, preview.bizType());
            if (command == null || command.bizId() == null) {
                ps.setObject(6, null);
            } else {
                ps.setLong(6, command.bizId());
            }
            ps.setString(7, preview.priority());
            ps.setString(8, receiverMode);
            ps.setInt(9, receiverCount);
            ps.setString(10, toJson(extractVariables(command)));
            ps.setString(11, toJson(normalizeReceiverIds(command == null ? null : command.receiverUserIds())));
            if (operatorId == null) {
                ps.setObject(12, null);
            } else {
                ps.setLong(12, operatorId);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("模板发布记录创建失败");
        }
        return key.longValue();
    }

    private void markPublishLogSuccess(Long logId, Long messageId) {
        jdbcTemplate.update("""
                UPDATE notification_template_publish_log
                SET message_id = ?, status = 'SUCCESS', error_message = NULL, updated_at = NOW()
                WHERE id = ?
                """, messageId, logId);
    }

    private void markPublishLogFailed(Long logId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE notification_template_publish_log
                SET status = 'FAILED', error_message = ?, updated_at = NOW()
                WHERE id = ?
                """, truncate(errorMessage, 500), logId);
    }

    private NotificationTemplatePublishLogView mapPublishLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NotificationTemplatePublishLogView(
                rs.getLong("id"),
                getNullableLong(rs.getObject("template_id")),
                rs.getString("template_code"),
                rs.getString("template_name"),
                getNullableLong(rs.getObject("message_id")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("notification_type"),
                rs.getString("biz_type"),
                getNullableLong(rs.getObject("biz_id")),
                rs.getString("priority"),
                rs.getString("receiver_mode"),
                rs.getInt("receiver_count"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("variables_json"),
                rs.getString("receiver_user_ids_json"),
                getNullableLong(rs.getObject("operator_id")),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private NotificationTemplatePreviewView render(NotificationTemplateView template, NotificationTemplatePublishCommand command) {
        Map<String, String> variables = extractVariables(command);
        String title = renderText(template.titleTemplate(), variables);
        String content = renderText(template.contentTemplate(), variables);
        String bizType = command != null && command.bizType() != null && !command.bizType().isBlank()
                ? command.bizType().trim()
                : template.bizType();
        String priority = command != null && command.priority() != null && !command.priority().isBlank()
                ? normalizeUpper(command.priority(), template.priority())
                : template.priority();
        return new NotificationTemplatePreviewView(
                title,
                content,
                template.notificationType(),
                bizType,
                priority
        );
    }

    private String renderText(String text, Map<String, String> variables) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String value = variables.getOrDefault(name, "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private NotificationTemplateValidationView validateVariables(NotificationTemplateView template, Map<String, String> variables) {
        List<String> required = requiredVariables(template);
        Set<String> provided = new HashSet<>(variables.keySet());
        List<String> missing = required.stream()
                .filter(name -> !variables.containsKey(name) || variables.get(name) == null || variables.get(name).isBlank())
                .toList();
        List<String> extra = provided.stream()
                .filter(name -> !required.contains(name))
                .sorted()
                .toList();
        return new NotificationTemplateValidationView(missing.isEmpty(), required, missing, extra);
    }

    private List<String> requiredVariables(NotificationTemplateView template) {
        LinkedHashSet<String> variables = new LinkedHashSet<>();
        collectVariables(template.titleTemplate(), variables);
        collectVariables(template.contentTemplate(), variables);
        return variables.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private void collectVariables(String text, Set<String> variables) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (name != null && !name.isBlank()) {
                variables.add(name.trim());
            }
        }
    }

    private Map<String, String> extractVariables(NotificationTemplatePublishCommand command) {
        return command == null || command.variables() == null ? Map.of() : command.variables();
    }

    private long activeUserCount() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND status = 1", Long.class);
            return count == null ? 0L : count;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String nextCopyCode(String sourceCode) {
        String base = normalizeCode(sourceCode);
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        String candidate = base + "_COPY";
        if (!templateCodeExists(candidate)) {
            return candidate;
        }
        String suffix = "_COPY_" + System.currentTimeMillis() % 100000;
        int maxBaseLength = Math.max(2, 64 - suffix.length());
        return base.substring(0, Math.min(base.length(), maxBaseLength)) + suffix;
    }

    private boolean templateCodeExists(String code) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_template WHERE template_code = ? AND deleted = 0",
                Long.class,
                code
        );
        return count != null && count > 0;
    }

    private List<Long> normalizeReceiverIds(List<Long> receiverUserIds) {
        if (receiverUserIds == null || receiverUserIds.isEmpty()) {
            return List.of();
        }
        return receiverUserIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(10000)
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, String> readVariables(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> result = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
            return result == null ? Map.of() : result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Long> readReceiverIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<ArrayList<Long>>() {
            });
            return normalizeReceiverIds(ids);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void validate(NotificationTemplateCommand command) {
        if (command == null) {
            throw new BizException("模板参数不能为空");
        }
        if (command.templateCode() == null || command.templateCode().isBlank()) {
            throw new BizException("模板编码不能为空");
        }
        if (!normalizeCode(command.templateCode()).matches("^[A-Z0-9_:-]{2,64}$")) {
            throw new BizException("模板编码只允许字母、数字、下划线、冒号和短横线，长度 2-64");
        }
        if (command.templateName() == null || command.templateName().isBlank()) {
            throw new BizException("模板名称不能为空");
        }
        if (command.titleTemplate() == null || command.titleTemplate().isBlank()) {
            throw new BizException("标题模板不能为空");
        }
        if (command.contentTemplate() == null || command.contentTemplate().isBlank()) {
            throw new BizException("内容模板不能为空");
        }
    }

    private void bindTemplate(PreparedStatement ps, NotificationTemplateCommand command, String code, Long createdBy, Long updatedBy) throws java.sql.SQLException {
        ps.setString(1, code);
        ps.setString(2, command.templateName().trim());
        ps.setString(3, command.titleTemplate().trim());
        ps.setString(4, command.contentTemplate().trim());
        ps.setString(5, normalizeUpper(command.notificationType(), "SYSTEM"));
        ps.setString(6, blankToNull(command.bizType()));
        ps.setString(7, normalizeUpper(command.priority(), "NORMAL"));
        ps.setInt(8, Boolean.FALSE.equals(command.enabled()) ? 0 : 1);
        ps.setString(9, blankToNull(command.remark()));
        if (createdBy == null) {
            ps.setObject(10, null);
        } else {
            ps.setLong(10, createdBy);
        }
        if (updatedBy == null) {
            ps.setObject(11, null);
        } else {
            ps.setLong(11, updatedBy);
        }
    }

    private NotificationTemplateView mapTemplate(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NotificationTemplateView(
                rs.getLong("id"),
                rs.getString("template_code"),
                rs.getString("template_name"),
                rs.getString("title_template"),
                rs.getString("content_template"),
                rs.getString("notification_type"),
                rs.getString("biz_type"),
                rs.getString("priority"),
                rs.getInt("enabled") == 1,
                rs.getString("remark"),
                getNullableLong(rs.getObject("created_by")),
                getNullableLong(rs.getObject("updated_by")),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeUpper(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long getNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
