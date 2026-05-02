package com.vspicy.admin.dto;

import java.util.List;

public record SystemConfigBatchUpdateCommand(
        List<SystemConfigBatchUpdateItem> items,
        String changeReason
) {
}
