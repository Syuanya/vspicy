package com.vspicy.video.mq;

import com.vspicy.video.config.VideoTranscodeProperties;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.service.VideoTranscodeService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class VideoTranscodeDispatcher {
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

        boolean rocketMqEnabled = properties == null || properties.isRocketMq();
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();

        if (rocketMqEnabled && template != null) {
            try {
                VideoTranscodeMessage message = new VideoTranscodeMessage(transcodeTaskId, videoId);
                template.syncSend(topic(), message);
                return view(transcodeTaskId, "ROCKETMQ", false, true, "已分发到 RocketMQ", null);
            } catch (Exception ex) {
                return fallbackLocal(transcodeTaskId, "LOCAL_FALLBACK", ex.getMessage());
            }
        }

        return fallbackLocal(
                transcodeTaskId,
                "LOCAL_FALLBACK",
                rocketMqEnabled ? "RocketMQTemplate 不存在" : "RocketMQ dispatch disabled"
        );
    }

    public VideoTranscodeDispatchView dispatchLocal(Long transcodeTaskId) {
        return fallbackLocal(transcodeTaskId, "FORCE_LOCAL", null);
    }

    public VideoTranscodeDispatchView dispatchLocal(Long transcodeTaskId, Long videoId) {
        return dispatchLocal(transcodeTaskId);
    }

    public VideoTranscodeDispatchView health() {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        boolean rocketEnabled = properties == null || properties.isRocketMq();
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
                template == null ? "RocketMQTemplate 不存在，将使用本地 fallback" : "RocketMQTemplate 可用",
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
                properties == null || properties.isRocketMq(),
                true,
                fallbackUsed,
                success,
                message,
                error
        );
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
}
