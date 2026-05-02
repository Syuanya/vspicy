package com.vspicy.admin.dto;

public record LoginSecurityDailyItem(String date, long successCount, long failedCount) {
}
