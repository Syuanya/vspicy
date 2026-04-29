package com.vspicy.video.scheduler;

import com.vspicy.video.service.VideoStorageScanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class VideoStorageScanScheduler {
    private final VideoStorageScanService storageScanService;
    private final boolean enabled;
    private final String prefix;
    private final int limit;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VideoStorageScanScheduler(
            VideoStorageScanService storageScanService,
            @Value("${vspicy.video.storage.scan.enabled:false}") boolean enabled,
            @Value("${vspicy.video.storage.scan.prefix:videos/}") String prefix,
            @Value("${vspicy.video.storage.scan.limit:1000}") int limit
    ) {
        this.storageScanService = storageScanService;
        this.enabled = enabled;
        this.prefix = prefix;
        this.limit = limit;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.storage.scan.fixed-delay-ms:3600000}",
            initialDelayString = "${vspicy.video.storage.scan.initial-delay-ms:60000}"
    )
    public void scan() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = storageScanService.scan(prefix, limit);
            System.out.println("视频存储一致性扫描完成: dbMissingObject="
                    + result.dbMissingObjectCount()
                    + ", objectMissingDb="
                    + result.objectMissingDbCount());
        } catch (Exception ex) {
            System.err.println("视频存储一致性扫描失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
