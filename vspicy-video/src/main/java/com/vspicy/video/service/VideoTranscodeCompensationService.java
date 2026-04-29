package com.vspicy.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.video.config.VideoTranscodeCompensationProperties;
import com.vspicy.video.dto.TranscodeCompensationResponse;
import com.vspicy.video.entity.Video;
import com.vspicy.video.entity.VideoTranscodeTask;
import com.vspicy.video.mapper.VideoMapper;
import com.vspicy.video.mapper.VideoTranscodeTaskMapper;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class VideoTranscodeCompensationService {
    private final VideoTranscodeTaskMapper transcodeTaskMapper;
    private final VideoMapper videoMapper;
    private final VideoTranscodeDispatcher dispatcher;
    private final VideoTranscodeCompensationProperties properties;

    public VideoTranscodeCompensationService(
            VideoTranscodeTaskMapper transcodeTaskMapper,
            VideoMapper videoMapper,
            VideoTranscodeDispatcher dispatcher,
            VideoTranscodeCompensationProperties properties
    ) {
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.videoMapper = videoMapper;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vspicy.video.transcode.compensation.fixed-delay-ms:60000}")
    public void scheduledCompensate() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            TranscodeCompensationResponse response = compensate();
            if (response.submitted() > 0) {
                System.out.println("转码补偿扫描完成: " + response);
            }
        } catch (Exception ex) {
            System.err.println("转码补偿扫描失败: " + ex.getMessage());
        }
    }

    public TranscodeCompensationResponse compensate() {
        List<VideoTranscodeTask> candidates = loadCandidates();
        List<Long> submittedTaskIds = new ArrayList<>();

        int skipped = 0;
        for (VideoTranscodeTask task : candidates) {
            if (!canRetry(task)) {
                skipped++;
                continue;
            }

            Video video = videoMapper.selectById(task.getVideoId());
            if (video == null) {
                markFailed(task, "视频不存在，无法补偿");
                skipped++;
                continue;
            }

            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            task.setRetryCount(retryCount + 1);
            task.setStatus("PENDING");
            task.setErrorMessage(null);
            transcodeTaskMapper.updateById(task);

            video.setStatus("TRANSCODING");
            videoMapper.updateById(video);

            dispatcher.dispatch(task.getId(), task.getVideoId());
            submittedTaskIds.add(task.getId());
        }

        return new TranscodeCompensationResponse(
                candidates.size(),
                submittedTaskIds.size(),
                skipped,
                submittedTaskIds,
                "补偿扫描完成"
        );
    }

    private List<VideoTranscodeTask> loadCandidates() {
        LocalDateTime runningDeadline = LocalDateTime.now().minusMinutes(properties.getRunningTimeoutMinutes());
        int limit = Math.max(1, Math.min(properties.getPendingLimit(), 200));

        return transcodeTaskMapper.selectList(new LambdaQueryWrapper<VideoTranscodeTask>()
                .and(wrapper -> wrapper
                        .eq(VideoTranscodeTask::getStatus, "PENDING")
                        .or()
                        .eq(VideoTranscodeTask::getStatus, "FAILED")
                        .or(inner -> inner
                                .eq(VideoTranscodeTask::getStatus, "RUNNING")
                                .lt(VideoTranscodeTask::getUpdatedAt, runningDeadline)
                        )
                )
                .orderByAsc(VideoTranscodeTask::getId)
                .last("LIMIT " + limit));
    }

    private boolean canRetry(VideoTranscodeTask task) {
        if ("SUCCESS".equals(task.getStatus())) {
            return false;
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        return retryCount < properties.getMaxRetryCount();
    }

    private void markFailed(VideoTranscodeTask task, String reason) {
        task.setStatus("FAILED");
        task.setErrorMessage(reason);
        transcodeTaskMapper.updateById(task);
    }
}
