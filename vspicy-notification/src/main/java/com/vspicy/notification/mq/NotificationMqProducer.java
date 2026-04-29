package com.vspicy.notification.mq;

import com.vspicy.notification.config.NotificationMqProperties;
import com.vspicy.notification.dto.NotificationMqEventMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "vspicy.notification.mq", name = "enabled", havingValue = "true")
public class NotificationMqProducer {
    private final RocketMQTemplate rocketMQTemplate;
    private final NotificationMqProperties properties;

    public NotificationMqProducer(RocketMQTemplate rocketMQTemplate, NotificationMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public void send(NotificationMqEventMessage message) {
        rocketMQTemplate.syncSend(
                properties.getTopic(),
                MessageBuilder.withPayload(message)
                        .setHeader("KEYS", message.eventId())
                        .setHeader("TAGS", message.eventType())
                        .build()
        );
    }
}
