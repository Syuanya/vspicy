package com.vspicy.auth.service;

import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    public String hashForStorage(String rawPassword) {
        return rawPassword;
    }

    public boolean matches(String rawPassword, String storedPasswordHash) {
        if (rawPassword == null || storedPasswordHash == null) {
            return false;
        }

        // 开发阶段兼容明文密码。正式项目必须替换为 BCrypt / Argon2id。
        if (storedPasswordHash.equals(rawPassword)) {
            return true;
        }

        // 预留 BCrypt 格式识别位置，后续接 spring-security-crypto 后可替换。
        if (storedPasswordHash.startsWith("$2a$") || storedPasswordHash.startsWith("$2b$") || storedPasswordHash.startsWith("$2y$")) {
            return false;
        }

        return false;
    }
}
