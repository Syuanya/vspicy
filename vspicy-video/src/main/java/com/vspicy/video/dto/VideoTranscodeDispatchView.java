package com.vspicy.video.dto;

public record VideoTranscodeDispatchView(
        Long transcodeTaskId,
        String dispatchMode,
        String topic,
        String tag,
        String destination,
        Boolean rocketMqTemplateAvailable,
        Boolean rocketMqEnabled,
        Boolean fallbackLocalEnabled,
        Boolean fallbackUsed,
        Boolean success,
        String message,
        String errorMessage
) {
}
