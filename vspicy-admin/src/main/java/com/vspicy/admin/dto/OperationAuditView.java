package com.vspicy.admin.dto;

public record OperationAuditView(
        Long id,
        String traceId,
        String serviceName,
        String moduleName,
        String actionType,
        String operationName,
        String requestMethod,
        String requestUri,
        String requestParams,
        String requestBody,
        Integer responseStatus,
        Boolean success,
        String errorMessage,
        String riskLevel,
        String handleStatus,
        String handleRemark,
        Long operatorId,
        String operatorName,
        String operatorIp,
        String userAgent,
        Long costMs,
        Long handledBy,
        String handledAt,
        String createdAt
) {
}
