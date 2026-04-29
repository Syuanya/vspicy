package com.vspicy.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.TranscodeRetryResponse;
import com.vspicy.video.entity.Video;
import com.vspicy.video.entity.VideoTranscodeTask;
import com.vspicy.video.mapper.VideoMapper;
import com.vspicy.video.mapper.VideoTranscodeTaskMapper;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VideoTranscodeTaskService {
    private final VideoTranscodeTaskMapper transcodeTaskMapper;
    private final VideoMapper videoMapper;
    private final VideoTranscodeDispatcher dispatcher;

    public VideoTranscodeTaskService(
            VideoTranscodeTaskMapper transcodeTaskMapper,
            VideoMapper videoMapper,
            VideoTranscodeDispatcher dispatcher
    ) {
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.videoMapper = videoMapper;
        this.dispatcher = dispatcher;
    }

    public List<VideoTranscodeTask> list(String status, Long videoId, Integer limit) {
        LambdaQueryWrapper<VideoTranscodeTask> wrapper = new LambdaQueryWrapper<VideoTranscodeTask>()
                .orderByDesc(VideoTranscodeTask::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(VideoTranscodeTask::getStatus, status);
        }
        if (videoId != null) {
            wrapper.eq(VideoTranscodeTask::getVideoId, videoId);
        }

        int safeLimit = limit == null || limit <= 0 || limit > 200 ? 100 : limit;
        wrapper.last("LIMIT " + safeLimit);
        return transcodeTaskMapper.selectList(wrapper);
    }

    public VideoTranscodeTask getById(Long taskId) {
        VideoTranscodeTask task = transcodeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "转码任务不存在");
        }
        return task;
    }

    public TranscodeRetryResponse retryTask(Long taskId) {
        VideoTranscodeTask task = getById(taskId);
        Video video = videoMapper.selectById(task.getVideoId());
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }

        if ("RUNNING".equals(task.getStatus())) {
            return new TranscodeRetryResponse(task.getId(), task.getVideoId(), task.getStatus(), task.getRetryCount(), "任务正在执行，无需重试");
        }

        if ("SUCCESS".equals(task.getStatus()) && "PUBLISHED".equals(video.getStatus())) {
            return new TranscodeRetryResponse(task.getId(), task.getVideoId(), task.getStatus(), task.getRetryCount(), "任务已成功，无需重试");
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= 5) {
            throw new BizException(429, "重试次数已达上限");
        }

        task.setStatus("PENDING");
        task.setRetryCount(retryCount + 1);
        task.setErrorMessage(null);
        transcodeTaskMapper.updateById(task);

        video.setStatus("TRANSCODING");
        videoMapper.updateById(video);

        dispatcher.dispatch(task.getId(), task.getVideoId());

        return new TranscodeRetryResponse(task.getId(), task.getVideoId(), task.getStatus(), task.getRetryCount(), "已重新提交转码任务");
    }

    public TranscodeRetryResponse retryByVideoId(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }

        VideoTranscodeTask task = transcodeTaskMapper.selectOne(new LambdaQueryWrapper<VideoTranscodeTask>()
                .eq(VideoTranscodeTask::getVideoId, videoId)
                .orderByDesc(VideoTranscodeTask::getId)
                .last("LIMIT 1"));

        if (task == null) {
            throw new BizException(404, "该视频没有转码任务");
        }

        return retryTask(task.getId());
    }
}
