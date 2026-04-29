package com.vspicy.auth.dto;

public record LoginCommand(
        String username,
        String password
) {
}
