package com.vspicy.member.dto;

public record MemberCheckResponse(
        Long userId,
        String planCode,
        Boolean allowed,
        String reason,
        Long maxUploadMb
) {
}
