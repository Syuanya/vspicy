package com.vspicy.video.mq;

import com.vspicy.video.config.VideoTranscodeProperties;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.service.VideoTranscodeService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class VideoTranscodeDispatcher {
    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeDispatcher.class);

    private final VideoTranscodeProperties properties;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final VideoTranscodeService videoTranscodeService;

    public VideoTranscodeDispatcher(
            VideoTranscodeProperties properties,
            ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
            VideoTranscodeService videoTranscodeService
    ) {
        this.properties = properties;
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
        this.videoTranscodeService = videoTranscodeService;
    }

    public VideoTranscodeDispatchView dispatch(Long transcodeTaskId) {
        return dispatch(transcodeTaskId, null);
    }

    public VideoTranscodeDispatchView dispatch(Long transcodeTaskId, Long videoId) {
        if (transcodeTaskId == null) {
            return view(null, "INVALID", false, false, "transcodeTaskId 不能为空", null);
        }

        boolean rocketMqEnabled = rocketMqEnabled();
        if (!rocketMqEnabled) {
            log.info("RocketMQ 转码分发已关闭，使用本地转码 fallback. taskId={}, videoId={}", transcodeTaskId, videoId);
            return fallbackLocal(transcodeTaskId, "LOCAL_FALLBACK", "RocketMQ dispatch disabled");
        }

        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return fallbackLocal(transcodeTaskId, "LOCAL_FALLBACK", "RocketMQTemplate 不存在");
        }

        try {
            VideoTranscodeMessage message = new VideoTranscodeMessage(transcodeTaskId, videoId);
            template.syncSend(destination(), message, sendTimeoutMs());
            return view(transcodeTaskId, "ROCKETMQ", false, true, "已分发到 RocketMQ", null);
        } catch (Exception ex) {
            log.warn("RocketMQ 转码任务分发失败，切换本地转码 fallback. taskId={}, videoId={}, destination={}, error={}",
                    transcodeTaskId, videoId, destination(), ex.getMessage());
            return fallbackLocal(transcodeTaskId, "LOCAL_FALLBACK", ex.getMessage());
        }
    }

    public VideoTranscodeDispatchView dispatchLocal(Long transcodeTaskId) {
        return fallbackLocal(transcodeTaskId, "FORCE_LOCAL", null);
    }

    public VideoTranscodeDispatchView dispatchLocal(Long transcodeTaskId, Long videoId) {
        return dispatchLocal(transcodeTaskId);
    }

    public VideoTranscodeDispatchView health() {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        boolean rocketEnabled = rocketMqEnabled();
        return new VideoTranscodeDispatchView(
                null,
                "HEALTH",
                topic(),
                tag(),
                destination(),
                template != null,
                rocketEnabled,
                true,
                false,
                true,
                !rocketEnabled ? "RocketMQ 转码分发已关闭，将使用本地转码" : (template == null ? "RocketMQTemplate 不存在，将使用本地 fallback" : "RocketMQTemplate 可用"),
                null
        );
    }

    private VideoTranscodeDispatchView fallbackLocal(Long transcodeTaskId, String mode, String previousError) {
        try {
            videoTranscodeService.submitLocal(transcodeTaskId);
            return view(transcodeTaskId, mode, true, true, "已提交本地转码", previousError);
        } catch (Exception ex) {
            String error = previousError == null || previousError.isBlank()
                    ? ex.getMessage()
                    : previousError + " | fallback failed: " + ex.getMessage();
            return view(transcodeTaskId, mode, true, false, "本地 fallback 失败", error);
        }
    }

    private VideoTranscodeDispatchView view(Long taskId, String mode, boolean fallbackUsed, boolean success, String message, String error) {
        return new VideoTranscodeDispatchView(
                taskId,
                mode,
                topic(),
                tag(),
                destination(),
                rocketMQTemplateProvider.getIfAvailable() != null,
                rocketMqEnabled(),
                true,
                fallbackUsed,
                success,
                message,
                error
        );
    }

    private boolean rocketMqEnabled() {
        return properties != null && (properties.isRocketMq() || properties.isRocketmq());
    }

    private String topic() {
        return properties == null || properties.getTopic() == null ? "vspicy-video-transcode-topic" : properties.getTopic();
    }

    private String tag() {
        return properties == null || properties.getTag() == null ? "TRANSCODE" : properties.getTag();
    }

    private String destination() {
        return topic() + ":" + tag();
    }

    private long sendTimeoutMs() {
        Long value = properties == null ? null : properties.getSendTimeoutMs();
        return value == null || value <= 0 ? 3000L : value;
    }
}
