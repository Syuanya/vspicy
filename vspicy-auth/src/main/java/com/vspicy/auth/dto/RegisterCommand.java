package com.vspicy.auth.dto;

public record RegisterCommand(
        String username,
        String password,
        String nickname,
        String email,
        String phone
) {
}
