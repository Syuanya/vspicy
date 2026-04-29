package com.vspicy.video.scheduler;

import com.vspicy.video.service.VideoUploadQuotaReconcileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class VideoUploadQuotaReconcileScheduler {
    private final VideoUploadQuotaReconcileService reconcileService;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VideoUploadQuotaReconcileScheduler(
            VideoUploadQuotaReconcileService reconcileService,
            @Value("${vspicy.video.upload.quota.reconcile.enabled:false}") boolean enabled
    ) {
        this.reconcileService = reconcileService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.upload.quota.reconcile.fixed-delay-ms:3600000}",
            initialDelayString = "${vspicy.video.upload.quota.reconcile.initial-delay-ms:60000}"
    )
    public void reconcile() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = reconcileService.reconcileAll();
            System.out.println("上传配额一致性定时校准完成: " + result);
        } catch (Exception ex) {
            System.err.println("上传配额一致性定时校准失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
