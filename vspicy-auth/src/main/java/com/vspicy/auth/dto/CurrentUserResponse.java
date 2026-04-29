package com.vspicy.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        Long userId,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        String phone,
        Integer userType,
        List<String> roles,
        List<String> permissions
) {
}
