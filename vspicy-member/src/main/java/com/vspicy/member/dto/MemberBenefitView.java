package com.vspicy.member.dto;

public record MemberBenefitView(
        String planCode,
        String benefitCode,
        String benefitName,
        String benefitValue
) {
}
