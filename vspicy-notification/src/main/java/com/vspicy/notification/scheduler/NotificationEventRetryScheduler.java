package com.vspicy.notification.scheduler;

import com.vspicy.notification.config.NotificationMqProperties;
import com.vspicy.notification.lock.JdbcDistributedLockService;
import com.vspicy.notification.service.NotificationEventDispatchService;
import com.vspicy.notification.service.NotificationEventLogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NotificationEventRetryScheduler {
    private final NotificationMqProperties properties;
    private final NotificationEventLogService eventLogService;
    private final NotificationEventDispatchService dispatchService;
    private final JdbcDistributedLockService distributedLockService;
    private final AtomicBoolean localRunning = new AtomicBoolean(false);

    public NotificationEventRetryScheduler(
            NotificationMqProperties properties,
            NotificationEventLogService eventLogService,
            NotificationEventDispatchService dispatchService,
            JdbcDistributedLockService distributedLockService
    ) {
        this.properties = properties;
        this.eventLogService = eventLogService;
        this.dispatchService = dispatchService;
        this.distributedLockService = distributedLockService;
    }

    @Scheduled(fixedDelayString = "${vspicy.notification.mq.retry-fixed-delay-ms:60000}")
    public void retryFailedEvents() {
        if (!properties.isAutoRetryEnabled()) {
            return;
        }

        if (!localRunning.compareAndSet(false, true)) {
            return;
        }

        boolean locked = false;
        try {
            locked = tryDistributedLock();
            if (!locked) {
                return;
            }

            markDeadCandidates();
            retryCandidates();
        } finally {
            if (locked && properties.isRetryLockEnabled()) {
                distributedLockService.unlock(properties.getRetryLockKey());
            }
            localRunning.set(false);
        }
    }

    private boolean tryDistributedLock() {
        if (!properties.isRetryLockEnabled()) {
            return true;
        }

        boolean locked = distributedLockService.tryLock(
                properties.getRetryLockKey(),
                properties.getRetryLockLeaseSeconds()
        );

        if (locked) {
            System.out.println("获取通知重试分布式锁成功: lockKey="
                    + properties.getRetryLockKey()
                    + ", ownerId="
                    + distributedLockService.ownerId());
        } else {
            System.out.println("获取通知重试分布式锁失败，跳过本轮任务: lockKey="
                    + properties.getRetryLockKey()
                    + ", ownerId="
                    + distributedLockService.ownerId());
        }

        return locked;
    }

    private void markDeadCandidates() {
        List<String> deadCandidates = eventLogService.deadCandidateEventIds(
                properties.getMaxRetryCount(),
                properties.getRetryScanLimit()
        );

        for (String eventId : deadCandidates) {
            try {
                eventLogService.markDead(
                        eventId,
                        "自动重试次数已达到上限 maxRetryCount=" + properties.getMaxRetryCount()
                );
            } catch (Exception ex) {
                System.err.println("通知事件标记 DEAD 失败: eventId=" + eventId + ", error=" + ex.getMessage());
            }
        }
    }

    private void retryCandidates() {
        List<String> retryableEventIds = eventLogService.retryableFailedEventIds(
                properties.getMaxRetryCount(),
                properties.getRetryScanLimit()
        );

        for (String eventId : retryableEventIds) {
            try {
                String result = dispatchService.retry(eventId);
                System.out.println("通知事件自动重试完成: eventId=" + eventId + ", result=" + result);
            } catch (Exception ex) {
                eventLogService.markFailed(eventId, "AUTO_RETRY_FAILED: " + ex.getMessage());
                System.err.println("通知事件自动重试失败: eventId=" + eventId + ", error=" + ex.getMessage());
            }
        }
    }
}
