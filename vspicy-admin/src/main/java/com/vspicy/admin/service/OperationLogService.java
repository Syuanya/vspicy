package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.entity.OperationLog;
import com.vspicy.admin.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogService {
    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public List<OperationLog> list(Long userId, String operationType, String status, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;

        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getId)
                .last("LIMIT " + safeLimit);

        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLog::getOperationType, operationType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OperationLog::getStatus, status);
        }

        return operationLogMapper.selectList(wrapper);
    }
}
