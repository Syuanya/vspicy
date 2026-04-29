package com.vspicy.notification.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.notification.config.NotificationMqProperties;
import com.vspicy.notification.dto.BusinessNotificationEventCommand;
import com.vspicy.notification.dto.NotificationMqEventMessage;
import com.vspicy.notification.mq.NotificationMqProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationEventDispatchService {
    private final NotificationMqProperties properties;
    private final ObjectProvider<NotificationMqProducer> producerProvider;
    private final NotificationEventProcessorService processorService;
    private final NotificationEventLogService eventLogService;

    public NotificationEventDispatchService(
            NotificationMqProperties properties,
            ObjectProvider<NotificationMqProducer> producerProvider,
            NotificationEventProcessorService processorService,
            NotificationEventLogService eventLogService
    ) {
        this.properties = properties;
        this.producerProvider = producerProvider;
        this.processorService = processorService;
        this.eventLogService = eventLogService;
    }

    public String dispatch(String eventType, BusinessNotificationEventCommand command, Long senderId) {
        NotificationMqEventMessage message = buildMessage(eventType, command, senderId);
        return dispatchMessage(message);
    }

    public String retry(String eventId) {
        NotificationMqEventMessage message = eventLogService.loadMessage(eventId);
        return dispatchMessage(message);
    }

    private String dispatchMessage(NotificationMqEventMessage message) {
        validate(message.eventType(), message.receiverUserId());

        boolean created = eventLogService.createPending(message);
        if (!created && eventLogService.isSuccess(message.eventId())) {
            return "重复事件已忽略，eventId=" + message.eventId();
        }

        if (!properties.isEnabled()) {
            if (properties.isFallbackToSync()) {
                Long messageId = processorService.process(message);
                return "MQ未启用，已同步投递 messageId=" + messageId + ", eventId=" + message.eventId();
            }
            throw new BizException("MQ 未启用");
        }

        NotificationMqProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            if (properties.isFallbackToSync()) {
                Long messageId = processorService.process(message);
                return "MQ Producer 不存在，已同步投递 messageId=" + messageId + ", eventId=" + message.eventId();
            }
            throw new BizException("MQ Producer 未初始化");
        }

        try {
            producer.send(message);
            eventLogService.markSent(message.eventId());
            return "MQ消息已发送 eventId=" + message.eventId();
        } catch (Exception ex) {
            eventLogService.markFailed(message.eventId(), "MQ_SEND_FAILED: " + ex.getMessage());

            if (properties.isFallbackToSync()) {
                Long messageId = processorService.process(message);
                return "MQ发送失败，已同步投递 messageId=" + messageId + ", eventId=" + message.eventId();
            }

            throw new BizException("MQ发送失败：" + ex.getMessage());
        }
    }

    private NotificationMqEventMessage buildMessage(String eventType, BusinessNotificationEventCommand command, Long senderId) {
        validate(eventType, command == null ? null : command.receiverUserId());

        return new NotificationMqEventMessage(
                UUID.randomUUID().toString(),
                eventType,
                command.receiverUserId(),
                command.bizId(),
                command.title(),
                command.content(),
                command.actorName(),
                command.result(),
                command.reason(),
                command.priority(),
                senderId,
                System.currentTimeMillis()
        );
    }

    private void validate(String eventType, Long receiverUserId) {
        if (eventType == null || eventType.isBlank()) {
            throw new BizException("eventType 不能为空");
        }
        if (receiverUserId == null) {
            throw new BizException("receiverUserId 不能为空");
        }
    }
}
