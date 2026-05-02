package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.SystemReleaseCheckCommand;
import com.vspicy.admin.dto.SystemReleaseCheckItemView;
import com.vspicy.admin.dto.SystemReleaseCommand;
import com.vspicy.admin.dto.SystemReleaseMetricItem;
import com.vspicy.admin.dto.SystemReleaseOverviewView;
import com.vspicy.admin.dto.SystemReleaseView;
import com.vspicy.admin.entity.SystemReleaseCheckItem;
import com.vspicy.admin.entity.SystemReleaseRecord;
import com.vspicy.admin.mapper.SystemReleaseCheckItemMapper;
import com.vspicy.admin.mapper.SystemReleaseRecordMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminSystemReleaseService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PLANNED", "RELEASING", "SUCCESS", "FAILED", "ROLLED_BACK", "CANCELLED");
    private static final Set<String> RISKS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "STAGING", "PROD");

    private final SystemReleaseRecordMapper releaseRecordMapper;
    private final SystemReleaseCheckItemMapper checkItemMapper;

    public AdminSystemReleaseService(SystemReleaseRecordMapper releaseRecordMapper,
                                     SystemReleaseCheckItemMapper checkItemMapper) {
        this.releaseRecordMapper = releaseRecordMapper;
        this.checkItemMapper = checkItemMapper;
    }

    public SystemReleaseOverviewView overview(Integer days) {
        int safeDays = days == null || days <= 0 || days > 365 ? 30 : days;
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(safeDays - 1L).atStartOfDay();
        List<SystemReleaseRecord> records = releaseRecordMapper.selectList(new LambdaQueryWrapper<SystemReleaseRecord>()
                .ge(SystemReleaseRecord::getCreatedAt, start)
                .orderByDesc(SystemReleaseRecord::getCreatedAt));
        return new SystemReleaseOverviewView(
                (long) records.size(),
                countStatus(records, "PLANNED"),
                countStatus(records, "RELEASING"),
                countStatus(records, "SUCCESS"),
                countStatus(records, "FAILED"),
                countStatus(records, "ROLLED_BACK"),
                records.stream().filter(item -> "HIGH".equals(item.getRiskLevel()) || "CRITICAL".equals(item.getRiskLevel())).count(),
                records.stream().filter(item -> item.getCreatedAt() != null && item.getCreatedAt().toLocalDate().equals(today)).count(),
                distribution(records, "status"),
                distribution(records, "environment"),
                distribution(records, "risk")
        );
    }

    public List<SystemReleaseView> list(String environment,
                                        String status,
                                        String riskLevel,
                                        String serviceName,
                                        String keyword,
                                        Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        LambdaQueryWrapper<SystemReleaseRecord> wrapper = new LambdaQueryWrapper<SystemReleaseRecord>()
                .orderByDesc(SystemReleaseRecord::getCreatedAt)
                .orderByDesc(SystemReleaseRecord::getId)
                .last("LIMIT " + safeLimit);
        if (hasText(environment)) {
            wrapper.eq(SystemReleaseRecord::getEnvironment, normalizeEnvironment(environment));
        }
        if (hasText(status)) {
            wrapper.eq(SystemReleaseRecord::getStatus, normalizeStatus(status));
        }
        if (hasText(riskLevel)) {
            wrapper.eq(SystemReleaseRecord::getRiskLevel, normalizeRisk(riskLevel));
        }
        if (hasText(serviceName)) {
            wrapper.like(SystemReleaseRecord::getServices, serviceName.trim());
        }
        if (hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(SystemReleaseRecord::getReleaseNo, value)
                    .or().like(SystemReleaseRecord::getVersionName, value)
                    .or().like(SystemReleaseRecord::getTitle, value)
                    .or().like(SystemReleaseRecord::getServices, value)
                    .or().like(SystemReleaseRecord::getGitCommit, value)
                    .or().like(SystemReleaseRecord::getImageTag, value));
        }
        return releaseRecordMapper.selectList(wrapper).stream().map(item -> toView(item, false)).toList();
    }

    public SystemReleaseView get(Long id) {
        return toView(mustGet(id), true);
    }

    @Transactional
    public SystemReleaseView create(SystemReleaseCommand command, Long operatorId) {
        if (command == null) {
            throw new BizException("发布版本参数不能为空");
        }
        requireText(command.releaseNo(), "发布单号不能为空");
        requireText(command.versionName(), "版本号不能为空");
        requireText(command.title(), "发布标题不能为空");
        if (releaseRecordMapper.selectCount(new LambdaQueryWrapper<SystemReleaseRecord>()
                .eq(SystemReleaseRecord::getReleaseNo, command.releaseNo().trim())) > 0) {
            throw new BizException("发布单号已存在");
        }
        SystemReleaseRecord record = new SystemReleaseRecord();
        fill(record, command, true);
        record.setStatus(normalizeOrDefault(command.status(), "DRAFT", STATUSES, "发布状态不合法"));
        record.setOperatorId(operatorId);
        releaseRecordMapper.insert(record);
        initDefaultChecks(record.getId());
        return get(record.getId());
    }

    @Transactional
    public SystemReleaseView update(Long id, SystemReleaseCommand command, Long operatorId) {
        SystemReleaseRecord record = mustGet(id);
        if ("RELEASING".equals(record.getStatus()) || "SUCCESS".equals(record.getStatus())) {
            throw new BizException("发布中或已成功的发布单不允许直接编辑");
        }
        fill(record, command, false);
        record.setOperatorId(operatorId == null ? record.getOperatorId() : operatorId);
        releaseRecordMapper.updateById(record);
        return get(id);
    }

    @Transactional
    public SystemReleaseView start(Long id, SystemReleaseCommand command, Long operatorId) {
        SystemReleaseRecord record = mustGet(id);
        record.setStatus("RELEASING");
        record.setStartedAt(LocalDateTime.now());
        record.setOperatorId(operatorId == null ? record.getOperatorId() : operatorId);
        record.setStatusNote(command == null ? "发布开始" : emptyToNull(command.statusNote()));
        releaseRecordMapper.updateById(record);
        return get(id);
    }

    @Transactional
    public SystemReleaseView success(Long id, SystemReleaseCommand command, Long operatorId) {
        SystemReleaseRecord record = mustGet(id);
        record.setStatus("SUCCESS");
        record.setFinishedAt(LocalDateTime.now());
        record.setOperatorId(operatorId == null ? record.getOperatorId() : operatorId);
        record.setStatusNote(command == null ? "发布成功" : emptyToNull(command.statusNote()));
        releaseRecordMapper.updateById(record);
        return get(id);
    }

    @Transactional
    public SystemReleaseView fail(Long id, SystemReleaseCommand command, Long operatorId) {
        SystemReleaseRecord record = mustGet(id);
        record.setStatus("FAILED");
        record.setFinishedAt(LocalDateTime.now());
        record.setOperatorId(operatorId == null ? record.getOperatorId() : operatorId);
        record.setStatusNote(command == null ? "发布失败" : emptyToNull(command.statusNote()));
        releaseRecordMapper.updateById(record);
        return get(id);
    }

    @Transactional
    public SystemReleaseView rollback(Long id, SystemReleaseCommand command, Long operatorId) {
        SystemReleaseRecord record = mustGet(id);
        record.setStatus("ROLLED_BACK");
        record.setRollbackAt(LocalDateTime.now());
        record.setFinishedAt(record.getFinishedAt() == null ? LocalDateTime.now() : record.getFinishedAt());
        record.setRollbackReason(command == null ? null : emptyToNull(command.rollbackReason()));
        record.setOperatorId(operatorId == null ? record.getOperatorId() : operatorId);
        record.setStatusNote(command == null ? "已回滚" : emptyToNull(command.statusNote()));
        releaseRecordMapper.updateById(record);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SystemReleaseRecord record = mustGet(id);
        if ("RELEASING".equals(record.getStatus())) {
            throw new BizException("发布中的记录不能删除");
        }
        checkItemMapper.delete(new LambdaQueryWrapper<SystemReleaseCheckItem>().eq(SystemReleaseCheckItem::getReleaseId, id));
        releaseRecordMapper.deleteById(id);
    }

    public List<SystemReleaseCheckItemView> checks(Long releaseId) {
        mustGet(releaseId);
        return listChecks(releaseId);
    }

    @Transactional
    public SystemReleaseCheckItemView createCheck(Long releaseId, SystemReleaseCheckCommand command) {
        mustGet(releaseId);
        if (command == null || !hasText(command.checkName())) {
            throw new BizException("检查项名称不能为空");
        }
        SystemReleaseCheckItem item = new SystemReleaseCheckItem();
        item.setReleaseId(releaseId);
        fillCheck(item, command);
        item.setStatus(normalizeCheckStatus(command.status(), "PENDING"));
        checkItemMapper.insert(item);
        return toCheckView(mustGetCheck(item.getId()));
    }

    @Transactional
    public SystemReleaseCheckItemView updateCheck(Long checkId, SystemReleaseCheckCommand command) {
        SystemReleaseCheckItem item = mustGetCheck(checkId);
        fillCheck(item, command);
        checkItemMapper.updateById(item);
        return toCheckView(mustGetCheck(checkId));
    }

    @Transactional
    public SystemReleaseCheckItemView passCheck(Long checkId, SystemReleaseCheckCommand command, Long operatorId) {
        SystemReleaseCheckItem item = mustGetCheck(checkId);
        item.setStatus("PASS");
        item.setResultNote(command == null ? item.getResultNote() : emptyToNull(command.resultNote()));
        item.setCheckedBy(operatorId);
        item.setCheckedAt(LocalDateTime.now());
        checkItemMapper.updateById(item);
        return toCheckView(mustGetCheck(checkId));
    }

    @Transactional
    public SystemReleaseCheckItemView failCheck(Long checkId, SystemReleaseCheckCommand command, Long operatorId) {
        SystemReleaseCheckItem item = mustGetCheck(checkId);
        item.setStatus("FAIL");
        item.setResultNote(command == null ? item.getResultNote() : emptyToNull(command.resultNote()));
        item.setCheckedBy(operatorId);
        item.setCheckedAt(LocalDateTime.now());
        checkItemMapper.updateById(item);
        return toCheckView(mustGetCheck(checkId));
    }

    private void fill(SystemReleaseRecord record, SystemReleaseCommand command, boolean create) {
        if (command == null) {
            return;
        }
        if (create || hasText(command.releaseNo())) record.setReleaseNo(trim(command.releaseNo()));
        if (create || hasText(command.versionName())) record.setVersionName(trim(command.versionName()));
        if (create || hasText(command.environment())) record.setEnvironment(normalizeOrDefault(command.environment(), "TEST", ENVIRONMENTS, "发布环境不合法"));
        if (create || hasText(command.riskLevel())) record.setRiskLevel(normalizeOrDefault(command.riskLevel(), "LOW", RISKS, "风险等级不合法"));
        if (create || hasText(command.title())) record.setTitle(trim(command.title()));
        record.setDescription(emptyToNull(command.description()));
        record.setServices(emptyToNull(command.services()));
        record.setGitBranch(emptyToNull(command.gitBranch()));
        record.setGitCommit(emptyToNull(command.gitCommit()));
        record.setImageTag(emptyToNull(command.imageTag()));
        record.setReleaseNote(emptyToNull(command.releaseNote()));
        record.setStatusNote(emptyToNull(command.statusNote()));
        record.setReviewerId(command.reviewerId());
        LocalDateTime plannedAt = parseDateTime(command.plannedAt());
        if (plannedAt != null || create) {
            record.setPlannedAt(plannedAt);
        }
    }

    private void fillCheck(SystemReleaseCheckItem item, SystemReleaseCheckCommand command) {
        if (command == null) {
            return;
        }
        if (hasText(command.checkName())) item.setCheckName(trim(command.checkName()));
        if (hasText(command.checkType())) item.setCheckType(trim(command.checkType()).toUpperCase(Locale.ROOT));
        if (hasText(command.status())) item.setStatus(normalizeCheckStatus(command.status(), "PENDING"));
        item.setResultNote(emptyToNull(command.resultNote()));
        item.setSortNo(command.sortNo() == null ? (item.getSortNo() == null ? 100 : item.getSortNo()) : command.sortNo());
    }

    private void initDefaultChecks(Long releaseId) {
        insertCheck(releaseId, "服务健康检查", "SMOKE", 10);
        insertCheck(releaseId, "数据库变更确认", "DATABASE", 20);
        insertCheck(releaseId, "核心接口冒烟", "SMOKE", 30);
        insertCheck(releaseId, "回滚方案确认", "ROLLBACK", 40);
    }

    private void insertCheck(Long releaseId, String name, String type, int sortNo) {
        SystemReleaseCheckItem item = new SystemReleaseCheckItem();
        item.setReleaseId(releaseId);
        item.setCheckName(name);
        item.setCheckType(type);
        item.setStatus("PENDING");
        item.setSortNo(sortNo);
        checkItemMapper.insert(item);
    }

    private SystemReleaseRecord mustGet(Long id) {
        if (id == null) {
            throw new BizException("发布记录ID不能为空");
        }
        SystemReleaseRecord record = releaseRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException("发布记录不存在");
        }
        return record;
    }

    private SystemReleaseCheckItem mustGetCheck(Long id) {
        if (id == null) {
            throw new BizException("检查项ID不能为空");
        }
        SystemReleaseCheckItem item = checkItemMapper.selectById(id);
        if (item == null) {
            throw new BizException("发布检查项不存在");
        }
        return item;
    }

    private SystemReleaseView toView(SystemReleaseRecord record, boolean withChecks) {
        List<SystemReleaseCheckItemView> checks = withChecks ? listChecks(record.getId()) : List.of();
        long total = checkItemMapper.selectCount(new LambdaQueryWrapper<SystemReleaseCheckItem>().eq(SystemReleaseCheckItem::getReleaseId, record.getId()));
        long pass = checkItemMapper.selectCount(new LambdaQueryWrapper<SystemReleaseCheckItem>().eq(SystemReleaseCheckItem::getReleaseId, record.getId()).eq(SystemReleaseCheckItem::getStatus, "PASS"));
        long fail = checkItemMapper.selectCount(new LambdaQueryWrapper<SystemReleaseCheckItem>().eq(SystemReleaseCheckItem::getReleaseId, record.getId()).eq(SystemReleaseCheckItem::getStatus, "FAIL"));
        return new SystemReleaseView(
                record.getId(), record.getReleaseNo(), record.getVersionName(), record.getEnvironment(), record.getStatus(), record.getRiskLevel(),
                record.getTitle(), record.getDescription(), record.getServices(), record.getGitBranch(), record.getGitCommit(), record.getImageTag(),
                record.getReleaseNote(), record.getOperatorId(), record.getReviewerId(), record.getPlannedAt(), record.getStartedAt(), record.getFinishedAt(),
                record.getRollbackAt(), record.getRollbackReason(), record.getStatusNote(), total, pass, fail, record.getCreatedAt(), record.getUpdatedAt(), checks
        );
    }

    private List<SystemReleaseCheckItemView> listChecks(Long releaseId) {
        return checkItemMapper.selectList(new LambdaQueryWrapper<SystemReleaseCheckItem>()
                        .eq(SystemReleaseCheckItem::getReleaseId, releaseId)
                        .orderByAsc(SystemReleaseCheckItem::getSortNo)
                        .orderByAsc(SystemReleaseCheckItem::getId))
                .stream().map(this::toCheckView).toList();
    }

    private SystemReleaseCheckItemView toCheckView(SystemReleaseCheckItem item) {
        return new SystemReleaseCheckItemView(
                item.getId(), item.getReleaseId(), item.getCheckName(), item.getCheckType(), item.getStatus(), item.getResultNote(),
                item.getSortNo(), item.getCheckedBy(), item.getCheckedAt(), item.getCreatedAt(), item.getUpdatedAt()
        );
    }

    private long countStatus(List<SystemReleaseRecord> records, String status) {
        return records.stream().filter(item -> status.equals(item.getStatus())).count();
    }

    private List<SystemReleaseMetricItem> distribution(List<SystemReleaseRecord> records, String dimension) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (SystemReleaseRecord record : records) {
            String key = switch (dimension) {
                case "environment" -> value(record.getEnvironment(), "UNKNOWN");
                case "risk" -> value(record.getRiskLevel(), "UNKNOWN");
                default -> value(record.getStatus(), "UNKNOWN");
            };
            map.put(key, map.getOrDefault(key, 0L) + 1);
        }
        return map.entrySet().stream().map(item -> new SystemReleaseMetricItem(item.getKey(), item.getValue())).toList();
    }

    private String normalizeStatus(String status) {
        return normalizeOrDefault(status, "DRAFT", STATUSES, "发布状态不合法");
    }

    private String normalizeRisk(String riskLevel) {
        return normalizeOrDefault(riskLevel, "LOW", RISKS, "风险等级不合法");
    }

    private String normalizeEnvironment(String environment) {
        return normalizeOrDefault(environment, "TEST", ENVIRONMENTS, "发布环境不合法");
    }

    private String normalizeCheckStatus(String status, String defaultValue) {
        String value = hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!Set.of("PENDING", "PASS", "FAIL", "SKIPPED").contains(value)) {
            throw new BizException("检查项状态不合法");
        }
        return value;
    }

    private String normalizeOrDefault(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowed.contains(normalized)) {
            throw new BizException(message);
        }
        return normalized;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        try {
            if (text.length() == 16) {
                text = text + ":00";
            }
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (Exception ignored) {
            throw new BizException("时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BizException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String emptyToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String value(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }
}
