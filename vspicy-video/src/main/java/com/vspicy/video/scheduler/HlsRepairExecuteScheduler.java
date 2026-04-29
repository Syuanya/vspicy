package com.vspicy.video.scheduler;

import com.vspicy.video.dto.HlsRepairExecuteCommand;
import com.vspicy.video.service.HlsRepairExecuteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HlsRepairExecuteScheduler {
    private final HlsRepairExecuteService executeService;
    private final boolean enabled;
    private final int limit;
    private final boolean dryRun;
    private final boolean allowPending;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HlsRepairExecuteScheduler(
            HlsRepairExecuteService executeService,
            @Value("${vspicy.video.hls.repair.execute.enabled:false}") boolean enabled,
            @Value("${vspicy.video.hls.repair.execute.limit:5}") int limit,
            @Value("${vspicy.video.hls.repair.execute.dry-run:false}") boolean dryRun,
            @Value("${vspicy.video.hls.repair.execute.allow-pending:false}") boolean allowPending
    ) {
        this.executeService = executeService;
        this.enabled = enabled;
        this.limit = limit;
        this.dryRun = dryRun;
        this.allowPending = allowPending;
    }

    @Scheduled(
            fixedDelayString = "${vspicy.video.hls.repair.execute.fixed-delay-ms:60000}",
            initialDelayString = "${vspicy.video.hls.repair.execute.initial-delay-ms:30000}"
    )
    public void execute() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            var result = executeService.execute(new HlsRepairExecuteCommand(limit, dryRun, allowPending));
            System.out.println("HLS 修复任务定时执行完成: " + result);
        } catch (Exception ex) {
            System.err.println("HLS 修复任务定时执行失败: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}
