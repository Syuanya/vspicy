package com.vspicy.member.dto;

import java.util.List;

public record UserMembershipView(
        Long userId,
        String planCode,
        String planName,
        String status,
        String startAt,
        String endAt,
        Boolean active,
        Long maxUploadMb,
        List<MemberBenefitView> benefits
) {
}
