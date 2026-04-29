package com.vspicy.video.scheduler;

import com.vspicy.video.dto.OperationAuditAlertEventAutomationStatusView;
import com.vspicy.video.dto.OperationAuditAlertEventSyncCommand;
import com.vspicy.video.dto.OperationAuditAlertEventSyncResult;
import com.vspicy.video.service.OperationAuditAlertEventService;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OperationAuditAlertEventAutoSyncScheduler {
    private static final String PREFIX = "vspicy.operation-audit.alert-events.auto-sync.";

    private final OperationAuditAlertEventService alertEventService;
    private final Environment environment;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile String lastRunAt;
    private volatile String lastSuccessAt;
    private volatile String lastErrorAt;
    private volatile String lastErrorMessage;
    private volatile Long lastGeneratedCount;
    private volatile Long lastOpenCount;
    private volatile Long lastAckedCount;
    private volatile Long lastResolvedCount;
    private volatile String lastMessage;

    public OperationAuditAlertEventAutoSyncScheduler(
            OperationAuditAlertEventService alertEventService,
            Environment environment
    ) {
        this.alertEventService = alertEventService;
        this.environment = environment;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.operation-audit.alert-events.auto-sync.fixed-delay-ms:300000}",
            initialDelayString = "${vspicy.operation-audit.alert-events.auto-sync.initial-delay-ms:60000}"
    )
    public void scheduledSync() {
        if (!enabled()) {
            return;
        }

        syncOnce(defaultHours(), defaultLimit());
    }

    public OperationAuditAlertEventAutomationStatusView syncOnce(Integer hours, Integer limit) {
        int safeHours = normalizeHours(hours);
        int safeLimit = normalizeLimit(limit);

        if (!running.compareAndSet(false, true)) {
            lastMessage = "告警同步正在运行，已跳过本次触发";
            return status();
        }

        lastRunAt = now();

        try {
            OperationAuditAlertEventSyncResult result = alertEventService.sync(
                    new OperationAuditAlertEventSyncCommand(safeHours, safeLimit)
            );

            lastSuccessAt = now();
            lastErrorMessage = null;
            lastGeneratedCount = result.generatedCount();
            lastOpenCount = result.openCount();
            lastAckedCount = result.ackedCount();
            lastResolvedCount = result.resolvedCount();
            lastMessage = result.message();

            return status();
        } catch (Exception ex) {
            lastErrorAt = now();
            lastErrorMessage = rootMessage(ex);
            lastMessage = "告警自动同步失败：" + lastErrorMessage;
            return status();
        } finally {
            running.set(false);
        }
    }

    public OperationAuditAlertEventAutomationStatusView status() {
        return new OperationAuditAlertEventAutomationStatusView(
                now(),
                enabled(),
                running.get(),
                fixedDelayMs(),
                initialDelayMs(),
                defaultHours(),
                defaultLimit(),
                lastRunAt,
                lastSuccessAt,
                lastErrorAt,
                lastErrorMessage,
                lastGeneratedCount,
                lastOpenCount,
                lastAckedCount,
                lastResolvedCount,
                lastMessage
        );
    }

    private boolean enabled() {
        return environment.getProperty(PREFIX + "enabled", Boolean.class, false);
    }

    private long fixedDelayMs() {
        return environment.getProperty(PREFIX + "fixed-delay-ms", Long.class, 300000L);
    }

    private long initialDelayMs() {
        return environment.getProperty(PREFIX + "initial-delay-ms", Long.class, 60000L);
    }

    private int defaultHours() {
        return normalizeHours(environment.getProperty(PREFIX + "hours", Integer.class, 24));
    }

    private int defaultLimit() {
        return normalizeLimit(environment.getProperty(PREFIX + "limit", Integer.class, 100));
    }

    private int normalizeHours(Integer value) {
        if (value == null || value <= 0) {
            return 24;
        }
        return Math.min(value, 168);
    }

    private int normalizeLimit(Integer value) {
        if (value == null || value <= 0) {
            return 100;
        }
        return Math.min(value, 500);
    }

    private String now() {
        return LocalDateTime.now().toString();
    }

    private String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? ex.getMessage() : cause.getMessage();
    }
}
