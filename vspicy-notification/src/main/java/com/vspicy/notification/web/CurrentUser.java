package com.vspicy.notification.web;

import com.vspicy.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Long id(HttpServletRequest request) {
        if (request == null) {
            throw new BizException(401, "未登录");
        }
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            throw new BizException(401, "未登录");
        }
        try {
            Long userId = Long.valueOf(value.trim());
            if (userId <= 0) {
                throw new BizException(401, "用户身份无效");
            }
            return userId;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(401, "用户身份无效");
        }
    }

    public static Long requireUserId(HttpServletRequest request) {
        return id(request);
    }

    public static Long optionalId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Long userId = Long.valueOf(value.trim());
            return userId > 0 ? userId : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
