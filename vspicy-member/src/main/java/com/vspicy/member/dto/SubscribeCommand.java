package com.vspicy.member.dto;

public record SubscribeCommand(
        Long userId,
        String planCode,
        Integer months
) {
}
