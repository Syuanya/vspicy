package com.vspicy.auth.dto;

public record ProfileUpdateCommand(
        String nickname,
        String avatarUrl,
        String email,
        String phone
) {
}
