package com.vspicy.video.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.video.service.HlsRepairExecuteService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 可选 HLS 修复任务 MQ 消费者。
 *
 * 默认关闭：
 * vspicy.video.hls.repair.consumer.enabled=false
 *
 * 开启前请确认 RocketMQ name-server、consumer group、topic 均正常。
 */
@Component
@ConditionalOnProperty(prefix = "vspicy.video.hls.repair.consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${vspicy.video.hls.repair.dispatch.topic:vspicy-video-hls-repair-topic}",
        consumerGroup = "${vspicy.video.hls.repair.consumer.group:vspicy-video-hls-repair-consumer}"
)
public class HlsRepairRocketMqConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final HlsRepairExecuteService executeService;

    public HlsRepairRocketMqConsumer(ObjectMapper objectMapper, HlsRepairExecuteService executeService) {
        this.objectMapper = objectMapper;
        this.executeService = executeService;
    }

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            executeService.executeFromMessage(payload);
        } catch (Exception ex) {
            throw new RuntimeException("HLS 修复任务消费失败：" + ex.getMessage(), ex);
        }
    }
}
