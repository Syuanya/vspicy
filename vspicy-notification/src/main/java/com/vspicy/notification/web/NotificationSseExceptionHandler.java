package com.vspicy.notification.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.vspicy.notification")
public class NotificationSseExceptionHandler {

    @ExceptionHandler({
            IOException.class,
            AsyncRequestNotUsableException.class,
            IllegalStateException.class
    })
    public void handleSseClientAbort(Exception ex, HttpServletRequest request) throws Exception {
        if (isSseRequest(request) || isClientAbort(ex)) {
            // SSE 客户端断开属于正常生命周期：浏览器刷新、关闭页面、网络切换都会发生。
            // 这里返回 void，避免 GlobalExceptionHandler 尝试在 text/event-stream 中写 JSON。
            return;
        }

        throw ex;
    }

    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/api/notifications/stream")) {
            return true;
        }

        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    private boolean isClientAbort(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (
                    message.contains("你的主机中的软件中止了一个已建立的连接")
                            || message.contains("Broken pipe")
                            || message.contains("Connection reset")
                            || message.contains("An established connection was aborted")
            )) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
