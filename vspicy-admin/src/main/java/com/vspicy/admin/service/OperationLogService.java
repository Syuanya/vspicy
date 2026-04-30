package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.OperationLogCleanupCommand;
import com.vspicy.admin.dto.OperationLogCleanupView;
import com.vspicy.admin.dto.OperationLogDailyItem;
import com.vspicy.admin.dto.OperationLogMetricItem;
import com.vspicy.admin.dto.OperationLogOverviewView;
import com.vspicy.admin.entity.OperationLog;
import com.vspicy.admin.mapper.OperationLogMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OperationLogService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_QUERY_LIMIT = 1000;
    private static final int EXPORT_LIMIT = 5000;
    private static final long SLOW_THRESHOLD_MS = 1000L;

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public List<OperationLog> list(Long userId,
                                  String operationType,
                                  String status,
                                  String keyword,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  Integer limit) {
        int safeLimit = safeLimit(limit, DEFAULT_LIMIT, MAX_QUERY_LIMIT);
        return operationLogMapper.selectList(buildQuery(userId, operationType, status, keyword, startTime, endTime)
                .orderByDesc(OperationLog::getId)
                .last("LIMIT " + safeLimit));
    }

    public OperationLogOverviewView overview(Integer days) {
        int safeDays = days == null || days <= 0 || days > 180 ? 7 : days;
        LocalDateTime start = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        List<OperationLog> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .ge(OperationLog::getCreatedAt, start)
                .orderByDesc(OperationLog::getId)
                .last("LIMIT " + EXPORT_LIMIT));

        long total = logs.size();
        long success = logs.stream().filter(item -> "SUCCESS".equalsIgnoreCase(item.getStatus())).count();
        long failed = logs.stream().filter(item -> "FAILED".equalsIgnoreCase(item.getStatus())).count();
        long slow = logs.stream().filter(item -> item.getCostMs() != null && item.getCostMs() >= SLOW_THRESHOLD_MS).count();
        long uniqueUsers = logs.stream().map(OperationLog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()).size();
        long avgCost = total == 0 ? 0L : Math.round(logs.stream()
                .map(OperationLog::getCostMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0D));

        return new OperationLogOverviewView(
                safeDays,
                total,
                success,
                failed,
                slow,
                uniqueUsers,
                avgCost,
                ratio(success, total),
                ratio(failed, total),
                group(logs, OperationLog::getStatus, 10),
                group(logs, OperationLog::getOperationType, 10),
                topUsers(logs, 10),
                dailyTrend(logs, safeDays)
        );
    }

    public byte[] exportCsv(Long userId,
                            String operationType,
                            String status,
                            String keyword,
                            LocalDateTime startTime,
                            LocalDateTime endTime) {
        List<OperationLog> logs = operationLogMapper.selectList(buildQuery(userId, operationType, status, keyword, startTime, endTime)
                .orderByDesc(OperationLog::getId)
                .last("LIMIT " + EXPORT_LIMIT));

        StringBuilder builder = new StringBuilder();
        builder.append('\ufeff');
        builder.append("id,createdAt,userId,username,roles,operationType,operationTitle,status,costMs,requestMethod,requestUri,clientIp,errorMessage\n");
        for (OperationLog log : logs) {
            appendCsvRow(builder,
                    log.getId(),
                    log.getCreatedAt(),
                    log.getUserId(),
                    log.getUsername(),
                    log.getRoles(),
                    log.getOperationType(),
                    log.getOperationTitle(),
                    log.getStatus(),
                    log.getCostMs(),
                    log.getRequestMethod(),
                    log.getRequestUri(),
                    log.getClientIp(),
                    log.getErrorMessage());
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public OperationLogCleanupView cleanup(OperationLogCleanupCommand command) {
        int retentionDays = command == null || command.retentionDays() == null ? 90 : command.retentionDays();
        if (retentionDays < 7) {
            throw new BizException(400, "审计日志最少保留 7 天");
        }
        if (retentionDays > 3650) {
            throw new BizException(400, "保留天数不能超过 3650 天");
        }

        boolean dryRun = command == null || command.dryRun() == null || command.dryRun();
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .lt(OperationLog::getCreatedAt, beforeTime);
        Long matched = operationLogMapper.selectCount(wrapper);
        long deleted = 0L;
        if (!dryRun && matched != null && matched > 0) {
            deleted = operationLogMapper.delete(wrapper);
        }
        return new OperationLogCleanupView(retentionDays, dryRun, beforeTime, matched == null ? 0L : matched, deleted);
    }

    private LambdaQueryWrapper<OperationLog> buildQuery(Long userId,
                                                        String operationType,
                                                        String status,
                                                        String keyword,
                                                        LocalDateTime startTime,
                                                        LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLog::getOperationType, operationType.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OperationLog::getStatus, status.trim());
        }
        if (startTime != null) {
            wrapper.ge(OperationLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLog::getCreatedAt, endTime);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(q -> q.like(OperationLog::getUsername, value)
                    .or().like(OperationLog::getOperationTitle, value)
                    .or().like(OperationLog::getRequestUri, value)
                    .or().like(OperationLog::getClientIp, value)
                    .or().like(OperationLog::getErrorMessage, value));
        }
        return wrapper;
    }

    private int safeLimit(Integer limit, int defaultLimit, int maxLimit) {
        return limit == null || limit <= 0 || limit > maxLimit ? defaultLimit : limit;
    }

    private double ratio(long part, long total) {
        if (total <= 0) {
            return 0D;
        }
        return Math.round(part * 10000D / total) / 100D;
    }

    private List<OperationLogMetricItem> group(List<OperationLog> logs,
                                               java.util.function.Function<OperationLog, String> classifier,
                                               int limit) {
        return logs.stream()
                .collect(Collectors.groupingBy(item -> normalize(classifier.apply(item)), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new OperationLogMetricItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<OperationLogMetricItem> topUsers(List<OperationLog> logs, int limit) {
        return logs.stream()
                .collect(Collectors.groupingBy(this::userLabel, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new OperationLogMetricItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<OperationLogDailyItem> dailyTrend(List<OperationLog> logs, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate today = LocalDate.now();
        Map<String, List<OperationLog>> byDate = logs.stream()
                .filter(item -> item.getCreatedAt() != null)
                .collect(Collectors.groupingBy(item -> item.getCreatedAt().toLocalDate().format(formatter)));

        return java.util.stream.IntStream.range(0, days)
                .mapToObj(index -> today.minusDays(days - 1L - index))
                .map(date -> {
                    String key = date.format(formatter);
                    List<OperationLog> items = byDate.getOrDefault(key, List.of());
                    long total = items.size();
                    long success = items.stream().filter(item -> "SUCCESS".equalsIgnoreCase(item.getStatus())).count();
                    long failed = items.stream().filter(item -> "FAILED".equalsIgnoreCase(item.getStatus())).count();
                    long slow = items.stream().filter(item -> item.getCostMs() != null && item.getCostMs() >= SLOW_THRESHOLD_MS).count();
                    return new OperationLogDailyItem(key, total, success, failed, slow);
                })
                .toList();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private String userLabel(OperationLog log) {
        String username = log.getUsername();
        Long userId = log.getUserId();
        if (username != null && !username.isBlank()) {
            return userId == null ? username : username + " / " + userId;
        }
        return userId == null ? "UNKNOWN" : String.valueOf(userId);
    }

    private void appendCsvRow(StringBuilder builder, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csv(values[i]));
        }
        builder.append('\n');
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\n") || text.contains("\r") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
