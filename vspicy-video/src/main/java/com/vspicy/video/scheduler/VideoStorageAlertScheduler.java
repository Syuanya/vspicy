package com.vspicy.video.scheduler;

import com.vspicy.video.dto.VideoStorageAlertGenerateCommand;
import com.vspicy.video.service.VideoStorageAlertService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class VideoStorageAlertScheduler {
    private final VideoStorageAlertService alertService;
    private final boolean enabled;
    private final String prefix;
    private final int limit;
    private final int threshold;
    private final int hlsLimit;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VideoStorageAlertScheduler(
            VideoStorageAlertService alertService,
            @Value("${vspicy.video.storage.alert.enabled:false}") boolean enabled,
            @Value("${vspicy.video.storage.alert.prefix:videos/}") String prefix,
            @Value("${vspicy.video.storage.alert.limit:1000}") int limit,
            @Value("${vspicy.video.storage.alert.threshold:80}") int threshold,
            @Value("${vspicy.video.storage.alert.hls-limit:200}") int hlsLimit
    ) {
        this.alertService = alertService;
        this.enabled = enabled;
        this.prefix = prefix;
        this.limit = limit;
        this.threshold = threshold;
        this.hlsLimit = hlsLimit;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.storage.alert.fixed-delay-ms:3600000}",
            initialDelayString = "${vspicy.video.storage.alert.initial-delay-ms:60000}"
    )
    public void generate() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = alertService.generate(new VideoStorageAlertGenerateCommand(
                    prefix,
                    limit,
                    threshold,
                    hlsLimit
            ));
            System.out.println("视频存储告警生成完成: " + result);
        } catch (Exception ex) {
            System.err.println("视频存储告警生成失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
