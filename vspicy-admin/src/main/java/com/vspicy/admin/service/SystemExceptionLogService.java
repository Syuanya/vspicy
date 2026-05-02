package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.ExceptionLogCleanupCommand;
import com.vspicy.admin.dto.ExceptionLogCleanupView;
import com.vspicy.admin.dto.ExceptionLogDailyItem;
import com.vspicy.admin.dto.ExceptionLogMetricItem;
import com.vspicy.admin.dto.ExceptionLogOverviewView;
import com.vspicy.admin.dto.ExceptionLogResolveCommand;
import com.vspicy.admin.dto.ExceptionLogView;
import com.vspicy.admin.entity.SystemExceptionLog;
import com.vspicy.admin.mapper.SystemExceptionLogMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SystemExceptionLogService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("NEW", "PROCESSING", "RESOLVED", "IGNORED");

    private final SystemExceptionLogMapper exceptionLogMapper;

    public SystemExceptionLogService(SystemExceptionLogMapper exceptionLogMapper) {
        this.exceptionLogMapper = exceptionLogMapper;
    }

    public List<ExceptionLogView> list(String serviceName,
                                      String severity,
                                      String status,
                                      String keyword,
                                      String startTime,
                                      String endTime,
                                      Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        LambdaQueryWrapper<SystemExceptionLog> wrapper = new LambdaQueryWrapper<SystemExceptionLog>()
                .orderByDesc(SystemExceptionLog::getLastSeenAt)
                .orderByDesc(SystemExceptionLog::getId)
                .last("LIMIT " + safeLimit);

        if (hasText(serviceName)) {
            wrapper.eq(SystemExceptionLog::getServiceName, serviceName.trim());
        }
        if (hasText(severity)) {
            wrapper.eq(SystemExceptionLog::getSeverity, normalizeSeverity(severity));
        }
        if (hasText(status)) {
            wrapper.eq(SystemExceptionLog::getStatus, normalizeStatus(status));
        }
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (start != null) {
            wrapper.ge(SystemExceptionLog::getLastSeenAt, start);
        }
        if (end != null) {
            wrapper.le(SystemExceptionLog::getLastSeenAt, end);
        }
        if (hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(SystemExceptionLog::getTraceId, value)
                    .or().like(SystemExceptionLog::getExceptionType, value)
                    .or().like(SystemExceptionLog::getExceptionMessage, value)
                    .or().like(SystemExceptionLog::getRequestUri, value)
                    .or().like(SystemExceptionLog::getUsername, value)
                    .or().like(SystemExceptionLog::getClientIp, value));
        }
        return exceptionLogMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    public ExceptionLogOverviewView overview(Integer days) {
        int safeDays = days == null || days <= 0 || days > 180 ? 7 : days;
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(safeDays - 1L).atStartOfDay();
        List<SystemExceptionLog> logs = exceptionLogMapper.selectList(new LambdaQueryWrapper<SystemExceptionLog>()
                .ge(SystemExceptionLog::getLastSeenAt, start)
                .orderByDesc(SystemExceptionLog::getLastSeenAt));

        long total = logs.size();
        long newCount = countByStatus(logs, "NEW");
        long processing = countByStatus(logs, "PROCESSING");
        long resolved = countByStatus(logs, "RESOLVED");
        long ignored = countByStatus(logs, "IGNORED");
        long critical = logs.stream().filter(log -> "CRITICAL".equalsIgnoreCase(value(log.getSeverity()))).count();
        long todayNew = logs.stream()
                .filter(log -> log.getCreatedAt() != null && log.getCreatedAt().toLocalDate().equals(today))
                .count();
        Set<String> services = new HashSet<>();
        for (SystemExceptionLog log : logs) {
            if (hasText(log.getServiceName())) {
                services.add(log.getServiceName());
            }
        }

        return new ExceptionLogOverviewView(
                safeDays,
                total,
                newCount,
                processing,
                resolved,
                ignored,
                newCount + processing,
                critical,
                (long) services.size(),
                todayNew,
                distribution(logs, "severity"),
                distribution(logs, "status"),
                distribution(logs, "service"),
                distribution(logs, "type"),
                daily(logs, safeDays, today)
        );
    }

    public ExceptionLogView get(Long id) {
        return toView(mustGet(id));
    }

    @Transactional
    public ExceptionLogView resolve(Long id, ExceptionLogResolveCommand command, Long operatorId) {
        SystemExceptionLog log = mustGet(id);
        log.setStatus("RESOLVED");
        log.setResolvedBy(operatorId);
        log.setResolvedAt(LocalDateTime.now());
        log.setResolutionNote(command == null ? null : command.resolutionNote());
        exceptionLogMapper.updateById(log);
        return toView(mustGet(id));
    }

    @Transactional
    public ExceptionLogView ignore(Long id, ExceptionLogResolveCommand command, Long operatorId) {
        SystemExceptionLog log = mustGet(id);
        log.setStatus("IGNORED");
        log.setResolvedBy(operatorId);
        log.setResolvedAt(LocalDateTime.now());
        log.setResolutionNote(command == null ? null : command.resolutionNote());
        exceptionLogMapper.updateById(log);
        return toView(mustGet(id));
    }

    @Transactional
    public ExceptionLogView reopen(Long id) {
        SystemExceptionLog log = mustGet(id);
        log.setStatus("NEW");
        log.setResolvedBy(null);
        log.setResolvedAt(null);
        log.setResolutionNote(null);
        exceptionLogMapper.updateById(log);
        return toView(mustGet(id));
    }

    @Transactional
    public void delete(Long id) {
        mustGet(id);
        exceptionLogMapper.deleteById(id);
    }

    @Transactional
    public ExceptionLogCleanupView cleanup(ExceptionLogCleanupCommand command) {
        int retentionDays = command == null || command.retentionDays() == null || command.retentionDays() < 7
                ? 90 : command.retentionDays();
        boolean dryRun = command == null || !Boolean.FALSE.equals(command.dryRun());
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        LambdaQueryWrapper<SystemExceptionLog> wrapper = new LambdaQueryWrapper<SystemExceptionLog>()
                .lt(SystemExceptionLog::getLastSeenAt, before)
                .in(SystemExceptionLog::getStatus, List.of("RESOLVED", "IGNORED"));
        Long matched = exceptionLogMapper.selectCount(wrapper);
        long deleted = 0L;
        if (!dryRun && matched != null && matched > 0) {
            deleted = exceptionLogMapper.delete(wrapper);
        }
        return new ExceptionLogCleanupView(retentionDays, dryRun, matched == null ? 0L : matched, deleted);
    }

    private SystemExceptionLog mustGet(Long id) {
        if (id == null) {
            throw new BizException("异常日志 ID 不能为空");
        }
        SystemExceptionLog log = exceptionLogMapper.selectById(id);
        if (log == null) {
            throw new BizException(404, "异常日志不存在");
        }
        return log;
    }

    private long countByStatus(List<SystemExceptionLog> logs, String status) {
        return logs.stream().filter(log -> status.equalsIgnoreCase(value(log.getStatus()))).count();
    }

    private List<ExceptionLogMetricItem> distribution(List<SystemExceptionLog> logs, String type) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (SystemExceptionLog log : logs) {
            String key;
            if ("severity".equals(type)) {
                key = value(log.getSeverity(), "UNKNOWN");
            } else if ("status".equals(type)) {
                key = value(log.getStatus(), "UNKNOWN");
            } else if ("service".equals(type)) {
                key = value(log.getServiceName(), "unknown-service");
            } else {
                key = value(log.getExceptionType(), "UnknownException");
            }
            result.put(key, result.getOrDefault(key, 0L) + 1L);
        }
        return result.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(entry -> new ExceptionLogMetricItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ExceptionLogDailyItem> daily(List<SystemExceptionLog> logs, int days, LocalDate today) {
        List<ExceptionLogDailyItem> items = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long total = 0L;
            long newCount = 0L;
            long resolved = 0L;
            long critical = 0L;
            for (SystemExceptionLog log : logs) {
                LocalDate logDate = log.getLastSeenAt() == null ? null : log.getLastSeenAt().toLocalDate();
                if (!date.equals(logDate)) {
                    continue;
                }
                total++;
                if ("NEW".equalsIgnoreCase(value(log.getStatus()))) {
                    newCount++;
                }
                if ("RESOLVED".equalsIgnoreCase(value(log.getStatus()))) {
                    resolved++;
                }
                if ("CRITICAL".equalsIgnoreCase(value(log.getSeverity()))) {
                    critical++;
                }
            }
            items.add(new ExceptionLogDailyItem(date.toString(), total, newCount, resolved, critical));
        }
        return items;
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

    private String normalizeSeverity(String value) {
        String normalized = value == null ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        return SEVERITIES.contains(normalized) ? normalized : "MEDIUM";
    }

    private String normalizeStatus(String value) {
        String normalized = value == null ? "NEW" : value.trim().toUpperCase(Locale.ROOT);
        return STATUSES.contains(normalized) ? normalized : "NEW";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private ExceptionLogView toView(SystemExceptionLog log) {
        if (log == null) {
            return null;
        }
        return new ExceptionLogView(
                log.getId(),
                log.getTraceId(),
                log.getServiceName(),
                log.getEnvironment(),
                log.getSeverity(),
                log.getStatus(),
                log.getExceptionType(),
                log.getExceptionMessage(),
                log.getRequestMethod(),
                log.getRequestUri(),
                log.getRequestParams(),
                log.getUserId(),
                log.getUsername(),
                log.getClientIp(),
                log.getUserAgent(),
                log.getStackTrace(),
                log.getOccurrenceCount(),
                log.getFirstSeenAt(),
                log.getLastSeenAt(),
                log.getResolvedBy(),
                log.getResolvedAt(),
                log.getResolutionNote(),
                log.getCreatedAt(),
                log.getUpdatedAt()
        );
    }
}
