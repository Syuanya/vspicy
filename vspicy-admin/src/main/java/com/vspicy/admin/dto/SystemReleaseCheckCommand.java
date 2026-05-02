package com.vspicy.admin.dto;

public record SystemReleaseCheckCommand(
        String checkName,
        String checkType,
        String status,
        String resultNote,
        Integer sortNo
) {
}
