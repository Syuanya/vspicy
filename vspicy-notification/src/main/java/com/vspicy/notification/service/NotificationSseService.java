package com.vspicy.notification.service;

import com.vspicy.notification.dto.NotificationSseEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class NotificationSseService {
    private static final long SSE_TIMEOUT = 30L * 60L * 1000L;

    private final ConcurrentHashMap<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        userEmitters.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));

        safeSend(userId, emitter, "connected", new NotificationSseEvent(
                "CONNECTED",
                null,
                userId,
                "实时通知已连接",
                "SSE connected",
                "SYSTEM",
                "LOW",
                Instant.now().toEpochMilli()
        ));

        return emitter;
    }

    public void sendToUser(Long userId, NotificationSseEvent event) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            safeSend(userId, emitter, "notification", event);
        }
    }

    public void sendHeartbeat(Long userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        NotificationSseEvent heartbeat = new NotificationSseEvent(
                "HEARTBEAT",
                null,
                userId,
                "heartbeat",
                "heartbeat",
                "SYSTEM",
                "LOW",
                Instant.now().toEpochMilli()
        );

        for (SseEmitter emitter : emitters) {
            safeSend(userId, emitter, "heartbeat", heartbeat);
        }
    }

    public int onlineCount(Long userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    public int onlineUserCount() {
        return userEmitters.size();
    }

    public int onlineConnectionCount() {
        return userEmitters.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    private void safeSend(Long userId, SseEmitter emitter, String eventName, NotificationSseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(String.valueOf(event.timestamp()))
                    .data(event));
        } catch (Throwable ex) {
            // SSE 客户端断开是正常生命周期事件。
            // 注意：这里不能调用 emitter.complete() 或 completeWithError()。
            // 当响应已经不可用时，complete() 本身会触发 AsyncRequestNotUsableException，
            // 并被全局异常处理器记录为系统异常。
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);

        if (emitters.isEmpty()) {
            userEmitters.remove(userId);
        }
    }
}
