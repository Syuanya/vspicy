package com.vspicy.notification.dto;

public record NotificationTemplatePublishLogView(
        Long id,
        Long templateId,
        String templateCode,
        String templateName,
        Long messageId,
        String title,
        String content,
        String notificationType,
        String bizType,
        Long bizId,
        String priority,
        String receiverMode,
        Integer receiverCount,
        String status,
        String errorMessage,
        String variablesJson,
        String receiverUserIdsJson,
        Long operatorId,
        String createdAt,
        String updatedAt
) {
}
