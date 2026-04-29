package com.vspicy.member.dto;

public record MemberPlanView(
        Long id,
        String planCode,
        String planName,
        String description,
        Long priceCent,
        Integer durationDays,
        Integer levelNo,
        Long maxUploadMb
) {
}
