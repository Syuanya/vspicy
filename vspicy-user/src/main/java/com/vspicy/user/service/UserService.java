package com.vspicy.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.user.dto.UserDetailView;
import com.vspicy.user.dto.UserOverviewView;
import com.vspicy.user.dto.UserUpdateCommand;
import com.vspicy.user.entity.SysUser;
import com.vspicy.user.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {
    private final SysUserMapper sysUserMapper;

    public UserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public SysUser getById(Long id) {
        if (id == null) {
            throw new BizException(400, "用户 ID 不能为空");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(404, "用户不存在");
        }
        return sanitize(user);
    }

    public UserDetailView detail(Long id) {
        return new UserDetailView(getById(id), overview());
    }

    public List<SysUser> listUsers(String keyword, Integer status, Integer userType, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 500));
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .orderByDesc(SysUser::getId)
                .last("LIMIT " + safeLimit);

        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (userType != null) {
            wrapper.eq(SysUser::getUserType, userType);
        }
        if (keyword != null && !keyword.isBlank()) {
            String key = keyword.trim();
            wrapper.and(query -> query
                    .like(SysUser::getUsername, key)
                    .or()
                    .like(SysUser::getNickname, key)
                    .or()
                    .like(SysUser::getEmail, key)
                    .or()
                    .like(SysUser::getPhone, key));
        }

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        users.forEach(this::sanitize);
        return users;
    }

    public UserOverviewView overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime recentStart = LocalDateTime.now().minusDays(7);
        return new UserOverviewView(
                count(null, null, null, null),
                count(1, null, null, null),
                count(2, null, null, null),
                count(3, null, null, null),
                count(null, 1, null, null),
                count(null, 2, null, null),
                count(null, 9, null, null),
                count(null, null, todayStart, null),
                count(null, null, null, recentStart)
        );
    }

    @Transactional
    public SysUser updateStatus(Long userId, Integer status) {
        validateStatus(status);
        SysUser user = rawUser(userId);
        ensureSuperAdminSafe(user, status, user.getUserType());
        user.setStatus(status);
        sysUserMapper.updateById(user);
        return sanitize(user);
    }

    @Transactional
    public SysUser updateUserType(Long userId, Integer userType) {
        validateUserType(userType);
        SysUser user = rawUser(userId);
        ensureSuperAdminSafe(user, user.getStatus(), userType);
        user.setUserType(userType);
        sysUserMapper.updateById(user);
        return sanitize(user);
    }

    @Transactional
    public SysUser updateUser(Long userId, UserUpdateCommand command) {
        if (command == null) {
            throw new BizException(400, "用户更新参数不能为空");
        }
        SysUser user = rawUser(userId);
        Integer nextStatus = command.status() == null ? user.getStatus() : command.status();
        Integer nextUserType = command.userType() == null ? user.getUserType() : command.userType();
        validateStatus(nextStatus);
        validateUserType(nextUserType);
        ensureSuperAdminSafe(user, nextStatus, nextUserType);

        user.setNickname(normalize(command.nickname(), user.getNickname()));
        user.setAvatarUrl(normalizeNullable(command.avatarUrl()));
        String email = normalizeNullable(command.email());
        String phone = normalizeNullable(command.phone());
        ensureUnique(userId, "email", email);
        ensureUnique(userId, "phone", phone);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(nextStatus);
        user.setUserType(nextUserType);
        try {
            sysUserMapper.updateById(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "邮箱或手机号已被其他用户使用");
        }
        return sanitize(user);
    }

    @Transactional
    public SysUser resetPassword(Long userId, String password) {
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw new BizException(400, "新密码长度必须为 6-64 位");
        }
        SysUser user = rawUser(userId);
        // 当前 auth 模块仍处于开发态明文兼容模式。后续接入 BCrypt / Argon2id 后，这里应改为统一密码服务。
        user.setPasswordHash(password);
        sysUserMapper.updateById(user);
        return sanitize(user);
    }

    private Long count(Integer status, Integer userType, LocalDateTime createdAfter, LocalDateTime loginAfter) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0);
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (userType != null) {
            wrapper.eq(SysUser::getUserType, userType);
        }
        if (createdAfter != null) {
            wrapper.ge(SysUser::getCreatedAt, createdAfter);
        }
        if (loginAfter != null) {
            wrapper.ge(SysUser::getLastLoginAt, loginAfter);
        }
        return sysUserMapper.selectCount(wrapper);
    }

    private SysUser rawUser(Long id) {
        if (id == null) {
            throw new BizException(400, "用户 ID 不能为空");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private SysUser sanitize(SysUser user) {
        if (user != null) {
            user.setPasswordHash(null);
        }
        return user;
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 1 && status != 2 && status != 3)) {
            throw new BizException(400, "用户状态只能是 1=正常、2=禁用、3=注销");
        }
    }

    private void validateUserType(Integer userType) {
        if (userType == null || (userType != 1 && userType != 2 && userType != 9)) {
            throw new BizException(400, "用户类型只能是 1=普通用户、2=创作者、9=管理员");
        }
    }

    private void ensureSuperAdminSafe(SysUser user, Integer nextStatus, Integer nextUserType) {
        if (user == null || user.getId() == null || user.getId() != 1L) {
            return;
        }
        if (!Integer.valueOf(1).equals(nextStatus)) {
            throw new BizException(400, "不能禁用或注销内置超级管理员");
        }
        if (!Integer.valueOf(9).equals(nextUserType)) {
            throw new BizException(400, "不能降低内置超级管理员的用户类型");
        }
    }

    private String normalize(String value, String fallback) {
        String normalized = normalizeNullable(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureUnique(Long currentUserId, String column, String value) {
        if (value == null) {
            return;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .ne(SysUser::getId, currentUserId)
                .last("LIMIT 1");
        if ("email".equals(column)) {
            wrapper.eq(SysUser::getEmail, value);
        } else if ("phone".equals(column)) {
            wrapper.eq(SysUser::getPhone, value);
        } else {
            return;
        }
        if (sysUserMapper.selectOne(wrapper) != null) {
            throw new BizException(409, ("email".equals(column) ? "邮箱" : "手机号") + "已被其他用户使用");
        }
    }
}
