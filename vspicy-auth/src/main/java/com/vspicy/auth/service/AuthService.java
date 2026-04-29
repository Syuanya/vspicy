package com.vspicy.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.auth.dto.CurrentUserResponse;
import com.vspicy.auth.dto.LoginCommand;
import com.vspicy.auth.dto.LoginResponse;
import com.vspicy.auth.dto.ProfileUpdateCommand;
import com.vspicy.auth.dto.RegisterCommand;
import com.vspicy.auth.entity.SysUser;
import com.vspicy.auth.mapper.SysUserMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final AuthJwtTokenService jwtTokenService;

    public AuthService(
            SysUserMapper userMapper,
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService,
            AuthJwtTokenService jwtTokenService
    ) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginCommand command) {
        if (command == null || command.username() == null || command.username().isBlank()) {
            throw new BizException("用户名不能为空");
        }
        if (command.password() == null || command.password().isBlank()) {
            throw new BizException("密码不能为空");
        }

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, command.username())
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));

        if (user == null) {
            throw new BizException(401, "用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }

        if (!passwordService.matches(command.password(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }

        CurrentUserResponse currentUser = buildCurrentUser(user);
        return new LoginResponse(
                jwtTokenService.createAccessToken(currentUser),
                jwtTokenService.createRefreshToken(currentUser),
                "Bearer",
                jwtTokenService.accessTokenExpiresInSeconds(),
                currentUser
        );
    }

    @Transactional
    public LoginResponse register(RegisterCommand command) {
        if (command == null || command.username() == null || command.username().isBlank()) {
            throw new BizException("用户名不能为空");
        }
        if (command.password() == null || command.password().length() < 6) {
            throw new BizException("密码至少需要 6 位");
        }

        String username = command.username().trim();
        if (!username.matches("^[A-Za-z0-9_\\-]{3,32}$")) {
            throw new BizException("用户名只能包含字母、数字、下划线和中划线，长度 3-32 位");
        }

        if (existsBy("username", username)) {
            throw new BizException(409, "用户名已存在");
        }

        String email = normalizeBlank(command.email());
        String phone = normalizeBlank(command.phone());
        if (email != null && existsBy("email", email)) {
            throw new BizException(409, "邮箱已被使用");
        }
        if (phone != null && existsBy("phone", phone)) {
            throw new BizException(409, "手机号已被使用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(normalizeBlank(command.nickname()) == null ? username : command.nickname().trim());
        user.setPasswordHash(passwordService.hashForStorage(command.password()));
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        user.setUserType(1);
        user.setDeleted(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "用户名、邮箱或手机号已存在");
        }

        assignDefaultUserRole(user.getId());
        CurrentUserResponse currentUser = buildCurrentUser(userMapper.selectById(user.getId()));
        return new LoginResponse(
                jwtTokenService.createAccessToken(currentUser),
                jwtTokenService.createRefreshToken(currentUser),
                "Bearer",
                jwtTokenService.accessTokenExpiresInSeconds(),
                currentUser
        );
    }

    public CurrentUserResponse currentUser(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        ensureActive(user);

        return buildCurrentUser(user);
    }

    public Long resolveUserIdFromToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenService.parseUserId(authorization.substring(7));
    }

    public CurrentUserResponse updateProfile(Long userId, ProfileUpdateCommand command) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        ensureActive(user);
        String email = normalizeBlank(command == null ? null : command.email());
        String phone = normalizeBlank(command == null ? null : command.phone());
        if (email != null && existsByOtherUser("email", email, userId)) {
            throw new BizException(409, "邮箱已被使用");
        }
        if (phone != null && existsByOtherUser("phone", phone, userId)) {
            throw new BizException(409, "手机号已被使用");
        }

        String nickname = normalizeBlank(command == null ? null : command.nickname());
        user.setNickname(nickname == null ? user.getUsername() : nickname);
        user.setAvatarUrl(normalizeBlank(command == null ? null : command.avatarUrl()));
        user.setEmail(email);
        user.setPhone(phone);
        userMapper.updateById(user);
        return buildCurrentUser(userMapper.selectById(userId));
    }

    public LoginResponse devToken() {
        SysUser user = userMapper.selectById(1L);
        if (user == null) {
            throw new BizException(404, "开发用户不存在，请先执行 sql/19-vspicy-auth-jwt-schema.sql");
        }

        CurrentUserResponse currentUser = buildCurrentUser(user);
        return new LoginResponse(
                jwtTokenService.createAccessToken(currentUser),
                jwtTokenService.createRefreshToken(currentUser),
                "Bearer",
                jwtTokenService.accessTokenExpiresInSeconds(),
                currentUser
        );
    }

    private CurrentUserResponse buildCurrentUser(SysUser user) {
        List<String> roles = jdbcTemplate.query(
                """
                SELECT r.role_code
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                  AND r.status = 1
                ORDER BY r.id
                """,
                (rs, rowNum) -> rs.getString("role_code"),
                user.getId()
        );

        List<String> permissions = jdbcTemplate.query(
                """
                SELECT DISTINCT p.permission_code
                FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                JOIN sys_user_role ur ON ur.role_id = rp.role_id
                WHERE ur.user_id = ?
                  AND p.status = 1
                ORDER BY p.permission_code
                """,
                (rs, rowNum) -> rs.getString("permission_code"),
                user.getId()
        );

        if (roles.contains("SUPER_ADMIN") && !permissions.contains("*")) {
            permissions = new java.util.ArrayList<>(permissions);
            permissions.add("*");
        }

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getEmail(),
                user.getPhone(),
                user.getUserType(),
                roles,
                permissions
        );
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void ensureActive(SysUser user) {
        if (user.getDeleted() != null && user.getDeleted() != 0) {
            throw new BizException(404, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
    }

    private boolean existsBy(String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE " + column + " = ?",
                Integer.class,
                value
        );
        return count != null && count > 0;
    }

    private boolean existsByOtherUser(String column, String value, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE " + column + " = ? AND id <> ?",
                Integer.class,
                value,
                userId
        );
        return count != null && count > 0;
    }

    private void assignDefaultUserRole(Long userId) {
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO sys_user_role(user_id, role_id)
                SELECT ?, id FROM sys_role WHERE role_code = 'USER'
                """,
                userId
        );
    }
}
