package com.vspicy.notification.service;

import com.vspicy.notification.dto.BusinessNotificationEventCommand;
import com.vspicy.notification.dto.NotificationMqEventMessage;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventProcessorService {
    private final NotificationEventLogService eventLogService;
    private final NotificationEventService eventService;

    public NotificationEventProcessorService(
            NotificationEventLogService eventLogService,
            NotificationEventService eventService
    ) {
        this.eventLogService = eventLogService;
        this.eventService = eventService;
    }

    public Long process(NotificationMqEventMessage message) {
        if (message == null || message.eventId() == null || message.eventType() == null) {
            return null;
        }

        if (eventLogService.isSuccess(message.eventId())) {
            eventLogService.markSkipped(message.eventId(), "重复事件，已存在 SUCCESS 记录");
            return null;
        }

        eventLogService.createPending(message);

        try {
            Long messageId = deliver(message);
            eventLogService.markSuccess(message.eventId(), messageId);
            return messageId;
        } catch (Exception ex) {
            eventLogService.markFailed(message.eventId(), ex.getMessage());
            throw ex;
        }
    }

    private Long deliver(NotificationMqEventMessage message) {
        BusinessNotificationEventCommand command = new BusinessNotificationEventCommand(
                message.receiverUserId(),
                message.bizId(),
                message.title(),
                message.content(),
                message.actorName(),
                message.result(),
                message.reason(),
                message.priority()
        );

        return switch (message.eventType()) {
            case "TRANSCODE" -> eventService.transcode(command, message.senderId());
            case "AUDIT" -> eventService.audit(command, message.senderId());
            case "INTERACTION" -> eventService.interaction(command, message.senderId());
            case "SECURITY" -> eventService.security(command, message.senderId());
            case "CUSTOM" -> eventService.custom(command, message.senderId());
            default -> eventService.custom(command, message.senderId());
        };
    }
}
