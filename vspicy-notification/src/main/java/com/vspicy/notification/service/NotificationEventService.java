package com.vspicy.notification.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.notification.dto.BusinessNotificationEventCommand;
import com.vspicy.notification.dto.NotificationCreateCommand;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationEventService {
    private final NotificationService notificationService;

    public NotificationEventService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public Long transcode(BusinessNotificationEventCommand command, Long senderId) {
        validateReceiver(command);

        String result = normalize(command.result(), "SUCCESS");
        boolean success = "SUCCESS".equalsIgnoreCase(result) || "COMPLETED".equalsIgnoreCase(result);
        String title = success ? "视频转码完成" : "视频转码失败";
        String content = success
                ? "你的视频《" + safeTitle(command.title()) + "》已完成 HLS 转码，可以正常播放。"
                : "你的视频《" + safeTitle(command.title()) + "》转码失败，原因：" + normalize(command.reason(), "未知错误");

        return publish(
                command,
                title,
                content,
                "TRANSCODE",
                "VIDEO_TRANSCODE",
                success ? "NORMAL" : "HIGH",
                senderId
        );
    }

    public Long audit(BusinessNotificationEventCommand command, Long senderId) {
        validateReceiver(command);

        String result = normalize(command.result(), "APPROVED");
        boolean approved = "APPROVED".equalsIgnoreCase(result) || "PASS".equalsIgnoreCase(result);
        String title = approved ? "内容审核通过" : "内容审核未通过";
        String content = approved
                ? "你的内容《" + safeTitle(command.title()) + "》已审核通过。"
                : "你的内容《" + safeTitle(command.title()) + "》审核未通过，原因：" + normalize(command.reason(), "未填写原因");

        return publish(
                command,
                title,
                content,
                "AUDIT",
                "CONTENT_AUDIT",
                approved ? "NORMAL" : "HIGH",
                senderId
        );
    }

    public Long interaction(BusinessNotificationEventCommand command, Long senderId) {
        validateReceiver(command);

        String actor = normalize(command.actorName(), "有人");
        String title = normalize(command.title(), "你收到新的互动");
        String content = normalize(command.content(), actor + " 与你的内容发生了互动。");

        return publish(
                command,
                title,
                content,
                "INTERACTION",
                "CONTENT_INTERACTION",
                "NORMAL",
                senderId
        );
    }

    public Long security(BusinessNotificationEventCommand command, Long senderId) {
        validateReceiver(command);

        String title = normalize(command.title(), "安全提醒");
        String content = normalize(command.content(), "你的账号出现一次安全事件，请确认是否为本人操作。");

        return publish(
                command,
                title,
                content,
                "SECURITY",
                "SECURITY_EVENT",
                "HIGH",
                senderId
        );
    }

    public Long custom(BusinessNotificationEventCommand command, Long senderId) {
        validateReceiver(command);

        return publish(
                command,
                normalize(command.title(), "业务通知"),
                normalize(command.content(), "你收到一条新的业务通知。"),
                "SYSTEM",
                "CUSTOM_EVENT",
                normalize(command.priority(), "NORMAL"),
                senderId
        );
    }

    private Long publish(
            BusinessNotificationEventCommand command,
            String defaultTitle,
            String defaultContent,
            String notificationType,
            String bizType,
            String defaultPriority,
            Long senderId
    ) {
        NotificationCreateCommand createCommand = new NotificationCreateCommand(
                normalize(command.title(), defaultTitle),
                normalize(command.content(), defaultContent),
                notificationType,
                bizType,
                command.bizId(),
                normalize(command.priority(), defaultPriority),
                List.of(command.receiverUserId())
        );

        return notificationService.publishSystem(createCommand, senderId);
    }

    private void validateReceiver(BusinessNotificationEventCommand command) {
        if (command == null || command.receiverUserId() == null) {
            throw new BizException("receiverUserId 不能为空");
        }
    }

    private String safeTitle(String title) {
        return normalize(title, "未命名内容");
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
