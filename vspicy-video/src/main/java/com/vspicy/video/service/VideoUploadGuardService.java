package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.client.MemberClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class VideoUploadGuardService {
    private final MemberClient memberClient;

    public VideoUploadGuardService(MemberClient memberClient) {
        this.memberClient = memberClient;
    }

    public UploadCheckView checkUploadAllowed(Long userId, Long sizeMb) {
        Long safeUserId = userId == null ? 1L : userId;
        Long safeSizeMb = sizeMb == null || sizeMb < 0 ? 0L : sizeMb;

        Map<String, Object> response = memberClient.checkUpload(safeUserId, safeSizeMb);
        if (response == null) {
            throw new BizException("会员服务无响应");
        }

        Object code = response.get("code");
        if (code != null && !"0".equals(String.valueOf(code))) {
            throw new BizException("会员服务校验失败：" + response.get("message"));
        }

        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BizException("会员服务返回格式异常");
        }

        boolean allowed = Boolean.TRUE.equals(dataMap.get("allowed"));
        String planCode = stringValue(dataMap.get("planCode"));
        String reason = stringValue(dataMap.get("reason"));
        Long maxUploadMb = longValue(dataMap.get("maxUploadMb"));

        UploadCheckView view = new UploadCheckView(
                safeUserId,
                safeSizeMb,
                planCode,
                allowed,
                reason,
                maxUploadMb
        );

        if (!allowed) {
            throw new BizException(403, reason == null || reason.isBlank() ? "当前会员等级不允许上传该大小文件" : reason);
        }

        return view;
    }

    public UploadCheckView checkOnly(Long userId, Long sizeMb) {
        try {
            return checkUploadAllowed(userId, sizeMb);
        } catch (BizException ex) {
            Long safeUserId = userId == null ? 1L : userId;
            Long safeSizeMb = sizeMb == null || sizeMb < 0 ? 0L : sizeMb;
            return new UploadCheckView(safeUserId, safeSizeMb, null, false, ex.getMessage(), null);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    public record UploadCheckView(
            Long userId,
            Long sizeMb,
            String planCode,
            Boolean allowed,
            String reason,
            Long maxUploadMb
    ) {
    }
}
