package com.vspicy.user.dto;

public record UserUpdateCommand(
        String nickname,
        String avatarUrl,
        String email,
        String phone,
        Integer status,
        Integer userType
) {
}
