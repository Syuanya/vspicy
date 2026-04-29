package com.vspicy.video.scheduler;

import com.vspicy.video.dto.HlsRepairVerifyCommand;
import com.vspicy.video.service.HlsRepairVerifyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HlsRepairVerifyScheduler {
    private final HlsRepairVerifyService verifyService;
    private final boolean enabled;
    private final int limit;
    private final boolean dryRun;
    private final boolean markFailedOnError;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HlsRepairVerifyScheduler(
            HlsRepairVerifyService verifyService,
            @Value("${vspicy.video.hls.repair.verify.enabled:false}") boolean enabled,
            @Value("${vspicy.video.hls.repair.verify.limit:10}") int limit,
            @Value("${vspicy.video.hls.repair.verify.dry-run:false}") boolean dryRun,
            @Value("${vspicy.video.hls.repair.verify.mark-failed-on-error:false}") boolean markFailedOnError
    ) {
        this.verifyService = verifyService;
        this.enabled = enabled;
        this.limit = limit;
        this.dryRun = dryRun;
        this.markFailedOnError = markFailedOnError;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.hls.repair.verify.fixed-delay-ms:120000}",
            initialDelayString = "${vspicy.video.hls.repair.verify.initial-delay-ms:60000}"
    )
    public void verify() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = verifyService.verify(new HlsRepairVerifyCommand(limit, dryRun, markFailedOnError));
            System.out.println("HLS 修复任务定时复检完成: " + result);
        } catch (Exception ex) {
            System.err.println("HLS 修复任务定时复检失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
