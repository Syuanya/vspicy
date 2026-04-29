package com.vspicy.video.mq;

import com.vspicy.video.service.VideoTranscodeService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "vspicy-video-transcode-topic",
        consumerGroup = "vspicy-video-transcode-consumer"
)
public class VideoTranscodeConsumer implements RocketMQListener<VideoTranscodeMessage> {
    private final VideoTranscodeService videoTranscodeService;

    public VideoTranscodeConsumer(VideoTranscodeService videoTranscodeService) {
        this.videoTranscodeService = videoTranscodeService;
    }

    @Override
    public void onMessage(VideoTranscodeMessage message) {
        if (message == null || message.getTranscodeTaskId() == null) {
            return;
        }
        videoTranscodeService.runTranscode(message.getTranscodeTaskId());
    }
}
