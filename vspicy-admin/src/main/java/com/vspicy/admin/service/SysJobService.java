package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.SysJobCommand;
import com.vspicy.admin.dto.SysJobLogView;
import com.vspicy.admin.dto.SysJobMetricItem;
import com.vspicy.admin.dto.SysJobOverviewView;
import com.vspicy.admin.dto.SysJobRunResultView;
import com.vspicy.admin.dto.SysJobView;
import com.vspicy.admin.entity.SysJob;
import com.vspicy.admin.entity.SysJobLog;
import com.vspicy.admin.mapper.SysJobLogMapper;
import com.vspicy.admin.mapper.SysJobMapper;
import com.vspicy.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SysJobService {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_.:-]{2,127}$");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;

    public SysJobService(SysJobMapper sysJobMapper, SysJobLogMapper sysJobLogMapper) {
        this.sysJobMapper = sysJobMapper;
        this.sysJobLogMapper = sysJobLogMapper;
    }

    public SysJobOverviewView overview() {
        List<SysJob> jobs = sysJobMapper.selectList(new LambdaQueryWrapper<SysJob>().orderByAsc(SysJob::getJobGroup));
        LocalDateTime today = LocalDate.now().atStartOfDay();
        List<SysJobLog> recentLogs = sysJobLogMapper.selectList(new LambdaQueryWrapper<SysJobLog>()
                .ge(SysJobLog::getCreatedAt, today.minusDays(6))
                .orderByDesc(SysJobLog::getId)
                .last("LIMIT 5000"));

        long totalJobs = jobs.size();
        long enabledJobs = jobs.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count();
        long disabledJobs = totalJobs - enabledJobs;
        long totalRuns = jobs.stream().map(SysJob::getRunCount).filter(v -> v != null).mapToLong(Integer::longValue).sum();
        long failedRuns = jobs.stream().map(SysJob::getFailCount).filter(v -> v != null).mapToLong(Integer::longValue).sum();
        long todayRuns = recentLogs.stream().filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(today)).count();
        long todayFailedRuns = recentLogs.stream().filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(today))
                .filter(item -> "FAILED".equalsIgnoreCase(item.getRunStatus())).count();

        return new SysJobOverviewView(
                totalJobs,
                enabledJobs,
                disabledJobs,
                totalRuns,
                failedRuns,
                todayRuns,
                todayFailedRuns,
                ratio(totalRuns - failedRuns, totalRuns),
                groupJobs(jobs),
                groupStatus(jobs),
                groupRunStatus(recentLogs)
        );
    }

    public List<SysJobView> list(String group, Integer status, String keyword, Integer limit) {
        int safeLimit = safeLimit(limit);
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<SysJob>()
                .orderByAsc(SysJob::getJobGroup)
                .orderByAsc(SysJob::getJobName)
                .last("LIMIT " + safeLimit);
        if (group != null && !group.isBlank()) {
            wrapper.eq(SysJob::getJobGroup, group.trim());
        }
        if (status != null) {
            wrapper.eq(SysJob::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(q -> q.like(SysJob::getJobCode, value)
                    .or().like(SysJob::getJobName, value)
                    .or().like(SysJob::getInvokeTarget, value)
                    .or().like(SysJob::getDescription, value));
        }
        return sysJobMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    public SysJobView get(Long id) {
        return toView(mustGet(id));
    }

    @Transactional
    public SysJobView create(SysJobCommand command) {
        validate(command, true);
        String jobCode = command.jobCode().trim();
        SysJob existed = sysJobMapper.selectOne(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getJobCode, jobCode)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException("任务编码已存在");
        }
        SysJob job = new SysJob();
        apply(job, command, true);
        job.setRunCount(0);
        job.setFailCount(0);
        sysJobMapper.insert(job);
        return toView(job);
    }

    @Transactional
    public SysJobView update(Long id, SysJobCommand command) {
        SysJob job = mustGet(id);
        if (!editable(job)) {
            throw new BizException("系统内置任务不允许编辑");
        }
        validate(command, false);
        if (command.jobCode() != null && !command.jobCode().isBlank()) {
            String jobCode = command.jobCode().trim();
            SysJob existed = sysJobMapper.selectOne(new LambdaQueryWrapper<SysJob>()
                    .eq(SysJob::getJobCode, jobCode)
                    .ne(SysJob::getId, id)
                    .last("LIMIT 1"));
            if (existed != null) {
                throw new BizException("任务编码已存在");
            }
        }
        apply(job, command, false);
        sysJobMapper.updateById(job);
        return toView(mustGet(id));
    }

    @Transactional
    public SysJobView enable(Long id) {
        SysJob job = mustGet(id);
        job.setStatus(1);
        sysJobMapper.updateById(job);
        return toView(job);
    }

    @Transactional
    public SysJobView disable(Long id) {
        SysJob job = mustGet(id);
        job.setStatus(0);
        sysJobMapper.updateById(job);
        return toView(job);
    }

    @Transactional
    public void delete(Long id) {
        SysJob job = mustGet(id);
        if (!editable(job)) {
            throw new BizException("系统内置任务不允许删除");
        }
        sysJobMapper.deleteById(id);
    }

    @Transactional
    public SysJobRunResultView run(Long id, String triggerType, HttpServletRequest request) {
        SysJob job = mustGet(id);
        LocalDateTime started = LocalDateTime.now();
        long startNanos = System.nanoTime();
        SysJobLog log = new SysJobLog();
        log.setJobId(job.getId());
        log.setJobCode(job.getJobCode());
        log.setJobName(job.getJobName());
        log.setJobGroup(job.getJobGroup());
        log.setTriggerType(triggerType == null || triggerType.isBlank() ? "MANUAL" : triggerType.trim().toUpperCase(Locale.ROOT));
        log.setOperatorId(header(request, "X-User-Id"));
        log.setOperatorName(header(request, "X-Username"));
        log.setStartedAt(started);
        log.setCreatedAt(started);

        try {
            if (Integer.valueOf(0).equals(job.getStatus())) {
                throw new BizException("任务已停用，不能执行");
            }
            // 当前版本为任务治理中心，不在后台进程中直接执行危险反射/脚本。
            // run 接口用于生成人工触发记录，后续可接入 Quartz、XXL-JOB 或 MQ Worker。
            log.setRunStatus("SUCCESS");
            log.setRunMessage("任务已人工触发，等待后续接入调度 Worker 执行目标：" + safe(job.getInvokeTarget()));
            return finishRun(job, log, startNanos, null);
        } catch (Exception ex) {
            log.setRunStatus("FAILED");
            log.setErrorMessage(ex.getMessage());
            return finishRun(job, log, startNanos, ex);
        }
    }

    public List<SysJobLogView> logs(Long jobId, String status, Integer limit) {
        int safeLimit = safeLimit(limit);
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .orderByDesc(SysJobLog::getId)
                .last("LIMIT " + safeLimit);
        if (jobId != null) {
            wrapper.eq(SysJobLog::getJobId, jobId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysJobLog::getRunStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return sysJobLogMapper.selectList(wrapper).stream().map(this::toLogView).toList();
    }

    private SysJobRunResultView finishRun(SysJob job, SysJobLog log, long startNanos, Exception ex) {
        long costMs = Math.max((System.nanoTime() - startNanos) / 1_000_000L, 0L);
        LocalDateTime finished = LocalDateTime.now();
        log.setCostMs(costMs);
        log.setFinishedAt(finished);
        sysJobLogMapper.insert(log);

        job.setLastRunAt(finished);
        job.setLastRunStatus(log.getRunStatus());
        job.setLastError(log.getErrorMessage());
        job.setRunCount(value(job.getRunCount()) + 1);
        if ("FAILED".equalsIgnoreCase(log.getRunStatus())) {
            job.setFailCount(value(job.getFailCount()) + 1);
        }
        sysJobMapper.updateById(job);

        if (ex != null && !(ex instanceof BizException)) {
            throw new BizException("任务触发失败：" + ex.getMessage());
        }
        return new SysJobRunResultView(job.getId(), log.getId(), log.getRunStatus(),
                log.getRunStatus().equals("SUCCESS") ? log.getRunMessage() : log.getErrorMessage(), costMs);
    }

    private void validate(SysJobCommand command, boolean create) {
        if (command == null) {
            throw new BizException("任务内容不能为空");
        }
        if (create || command.jobCode() != null) {
            if (command.jobCode() == null || command.jobCode().isBlank()) {
                throw new BizException("任务编码不能为空");
            }
            if (!CODE_PATTERN.matcher(command.jobCode().trim()).matches()) {
                throw new BizException("任务编码只能包含小写字母、数字、下划线、点、冒号和短横线，且长度为 3-128");
            }
        }
        if (create || command.jobName() != null) {
            if (command.jobName() == null || command.jobName().isBlank()) {
                throw new BizException("任务名称不能为空");
            }
        }
        if (create || command.cronExpression() != null) {
            if (command.cronExpression() == null || command.cronExpression().isBlank()) {
                throw new BizException("Cron 表达式不能为空");
            }
        }
    }

    private void apply(SysJob job, SysJobCommand command, boolean create) {
        if (create || command.jobCode() != null) {
            job.setJobCode(command.jobCode().trim());
        }
        if (create || command.jobName() != null) {
            job.setJobName(command.jobName().trim());
        }
        if (create || command.jobGroup() != null) {
            job.setJobGroup(blankToDefault(command.jobGroup(), "default"));
        }
        if (create || command.jobType() != null) {
            job.setJobType(blankToDefault(command.jobType(), "JAVA"));
        }
        if (create || command.cronExpression() != null) {
            job.setCronExpression(command.cronExpression() == null ? null : command.cronExpression().trim());
        }
        if (create || command.invokeTarget() != null) {
            job.setInvokeTarget(command.invokeTarget());
        }
        if (create || command.jobParams() != null) {
            job.setJobParams(command.jobParams());
        }
        if (create || command.description() != null) {
            job.setDescription(command.description());
        }
        if (create || command.status() != null) {
            job.setStatus(command.status() == null ? 1 : normalizeStatus(command.status()));
        }
        if (create || command.allowConcurrent() != null) {
            job.setAllowConcurrent(Boolean.TRUE.equals(command.allowConcurrent()) ? 1 : 0);
        }
        if (create || command.misfirePolicy() != null) {
            job.setMisfirePolicy(command.misfirePolicy() == null ? 1 : command.misfirePolicy());
        }
        if (create || command.editable() != null) {
            job.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        }
    }

    private SysJob mustGet(Long id) {
        if (id == null) {
            throw new BizException("任务 ID 不能为空");
        }
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) {
            throw new BizException(404, "任务不存在");
        }
        return job;
    }

    private SysJobView toView(SysJob job) {
        return new SysJobView(job.getId(), job.getJobCode(), job.getJobName(), job.getJobGroup(), job.getJobType(),
                job.getCronExpression(), job.getInvokeTarget(), job.getJobParams(), job.getDescription(), job.getStatus(),
                Integer.valueOf(1).equals(job.getAllowConcurrent()), job.getMisfirePolicy(), value(job.getRunCount()),
                value(job.getFailCount()), job.getLastRunAt(), job.getNextRunAt(), job.getLastRunStatus(), job.getLastError(),
                editable(job), job.getCreatedAt(), job.getUpdatedAt());
    }

    private SysJobLogView toLogView(SysJobLog log) {
        return new SysJobLogView(log.getId(), log.getJobId(), log.getJobCode(), log.getJobName(), log.getJobGroup(),
                log.getTriggerType(), log.getRunStatus(), log.getRunMessage(), log.getErrorMessage(), log.getCostMs(),
                log.getOperatorId(), log.getOperatorName(), log.getStartedAt(), log.getFinishedAt(), log.getCreatedAt());
    }

    private List<SysJobMetricItem> groupJobs(List<SysJob> jobs) {
        return jobs.stream()
                .collect(Collectors.groupingBy(item -> blankToDefault(item.getJobGroup(), "default"), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(entry -> new SysJobMetricItem(entry.getKey(), entry.getValue())).toList();
    }

    private List<SysJobMetricItem> groupStatus(List<SysJob> jobs) {
        long enabled = jobs.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count();
        long disabled = jobs.size() - enabled;
        return List.of(new SysJobMetricItem("ENABLED", enabled), new SysJobMetricItem("DISABLED", disabled));
    }

    private List<SysJobMetricItem> groupRunStatus(List<SysJobLog> logs) {
        Map<String, Long> values = logs.stream()
                .collect(Collectors.groupingBy(item -> blankToDefault(item.getRunStatus(), "UNKNOWN"), LinkedHashMap::new, Collectors.counting()));
        return values.entrySet().stream().map(entry -> new SysJobMetricItem(entry.getKey(), entry.getValue())).toList();
    }

    private int safeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > MAX_LIMIT ? DEFAULT_LIMIT : limit;
    }

    private int normalizeStatus(Integer value) {
        return value == null || value != 0 ? 1 : 0;
    }

    private boolean editable(SysJob job) {
        return job == null || !Integer.valueOf(0).equals(job.getEditable());
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double ratio(long part, long total) {
        if (total <= 0) {
            return 0D;
        }
        return Math.round(part * 10000D / total) / 100D;
    }

    private String header(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
