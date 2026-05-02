package com.vspicy.user.dto;

public record UserOverviewView(
        Long totalUsers,
        Long activeUsers,
        Long disabledUsers,
        Long cancelledUsers,
        Long normalUsers,
        Long creatorUsers,
        Long adminUsers,
        Long todayNewUsers,
        Long recentLoginUsers
) {
}
