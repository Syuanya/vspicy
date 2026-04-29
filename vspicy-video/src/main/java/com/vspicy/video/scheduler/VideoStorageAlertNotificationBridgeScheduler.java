package com.vspicy.video.scheduler;

import com.vspicy.video.dto.VideoStorageAlertNotificationSyncCommand;
import com.vspicy.video.service.VideoStorageAlertNotificationBridgeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class VideoStorageAlertNotificationBridgeScheduler {
    private final VideoStorageAlertNotificationBridgeService bridgeService;
    private final boolean enabled;
    private final int limit;
    private final Long targetUserId;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VideoStorageAlertNotificationBridgeScheduler(
            VideoStorageAlertNotificationBridgeService bridgeService,
            @Value("${vspicy.video.storage.alert.notification.enabled:false}") boolean enabled,
            @Value("${vspicy.video.storage.alert.notification.limit:100}") int limit,
            @Value("${vspicy.video.storage.alert.notification.target-user-id:1}") Long targetUserId
    ) {
        this.bridgeService = bridgeService;
        this.enabled = enabled;
        this.limit = limit;
        this.targetUserId = targetUserId;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.storage.alert.notification.fixed-delay-ms:300000}",
            initialDelayString = "${vspicy.video.storage.alert.notification.initial-delay-ms:60000}"
    )
    public void sync() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = bridgeService.sync(new VideoStorageAlertNotificationSyncCommand(
                    limit,
                    "",
                    targetUserId
            ));
            System.out.println("视频存储告警通知桥接完成: " + result);
        } catch (Exception ex) {
            System.err.println("视频存储告警通知桥接失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
