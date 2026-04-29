package com.vspicy.notification.mq;

import com.vspicy.notification.dto.NotificationMqEventMessage;
import com.vspicy.notification.service.NotificationEventProcessorService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "vspicy.notification.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${vspicy.notification.mq.topic:vspicy-notification-event-topic}",
        consumerGroup = "${vspicy.notification.mq.consumer-group:vspicy-notification-consumer}"
)
public class NotificationMqConsumer implements RocketMQListener<NotificationMqEventMessage> {
    private final NotificationEventProcessorService processorService;

    public NotificationMqConsumer(NotificationEventProcessorService processorService) {
        this.processorService = processorService;
    }

    @Override
    public void onMessage(NotificationMqEventMessage message) {
        processorService.process(message);
    }
}
