package com.vspicy.admin.dto;

import java.util.List;

public record LoginSecurityOverviewView(
        long todayLoginCount,
        long todaySuccessCount,
        long todayFailedCount,
        double todaySuccessRate,
        long onlineSessionCount,
        long lockedSessionCount,
        long abnormalIpCount,
        List<LoginSecurityStatItem> statusDistribution,
        List<LoginSecurityStatItem> loginTypeDistribution,
        List<LoginSecurityDailyItem> dailyTrend
) {
}
