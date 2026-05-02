package com.vspicy.admin.dto;

public record OperationAuditCreateCommand(
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
        Long operatorId,
        String operatorName,
        String operatorIp,
        String userAgent,
        Long costMs
) {
}
