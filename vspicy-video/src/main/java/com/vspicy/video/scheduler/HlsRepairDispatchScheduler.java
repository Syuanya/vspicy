package com.vspicy.video.scheduler;

import com.vspicy.video.dto.HlsRepairDispatchCommand;
import com.vspicy.video.service.HlsRepairDispatchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HlsRepairDispatchScheduler {
    private final HlsRepairDispatchService dispatchService;
    private final boolean enabled;
    private final int limit;
    private final boolean dryRun;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HlsRepairDispatchScheduler(
            HlsRepairDispatchService dispatchService,
            @Value("${vspicy.video.hls.repair.dispatch.enabled:false}") boolean enabled,
            @Value("${vspicy.video.hls.repair.dispatch.limit:10}") int limit,
            @Value("${vspicy.video.hls.repair.dispatch.dry-run:false}") boolean dryRun
    ) {
        this.dispatchService = dispatchService;
        this.enabled = enabled;
        this.limit = limit;
        this.dryRun = dryRun;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.hls.repair.dispatch.fixed-delay-ms:60000}",
            initialDelayString = "${vspicy.video.hls.repair.dispatch.initial-delay-ms:30000}"
    )
    public void dispatch() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = dispatchService.dispatch(new HlsRepairDispatchCommand(limit, dryRun));
            System.out.println("HLS 修复任务定时分发完成: " + result);
        } catch (Exception ex) {
            System.err.println("HLS 修复任务定时分发失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
