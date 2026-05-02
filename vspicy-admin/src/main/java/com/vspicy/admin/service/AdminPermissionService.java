package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.*;
import com.vspicy.admin.entity.*;
import com.vspicy.admin.mapper.*;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminPermissionService {
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public AdminPermissionService(
            SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper,
            SysUserRoleMapper userRoleMapper,
            SysRolePermissionMapper rolePermissionMapper
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    public PermissionOverviewView overview() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>());
        List<SysPermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>());
        long roleTotal = roles.size();
        long enabledRoleTotal = roles.stream().filter(item -> item.getStatus() == null || item.getStatus() == 1).count();
        long disabledRoleTotal = roleTotal - enabledRoleTotal;
        long permissionTotal = permissions.size();
        long enabledPermissionTotal = permissions.stream().filter(item -> item.getStatus() == null || item.getStatus() == 1).count();
        long disabledPermissionTotal = permissionTotal - enabledPermissionTotal;
        long menuPermissionTotal = permissions.stream().filter(item -> "MENU".equalsIgnoreCase(safe(item.getPermissionType()))).count();
        long buttonPermissionTotal = permissions.stream().filter(item -> "BUTTON".equalsIgnoreCase(safe(item.getPermissionType()))).count();
        long apiPermissionTotal = permissions.stream().filter(item -> "API".equalsIgnoreCase(safe(item.getPermissionType()))).count();
        long userRoleBindingTotal = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>());
        long rolePermissionBindingTotal = rolePermissionMapper.selectCount(new LambdaQueryWrapper<SysRolePermission>());
        return new PermissionOverviewView(
                roleTotal,
                enabledRoleTotal,
                disabledRoleTotal,
                permissionTotal,
                enabledPermissionTotal,
                disabledPermissionTotal,
                menuPermissionTotal,
                buttonPermissionTotal,
                apiPermissionTotal,
                userRoleBindingTotal,
                rolePermissionBindingTotal
        );
    }

    public List<SysRole> listRoles() {
        return listRoles(null, null);
    }

    public List<SysRole> listRoles(String keyword, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getStatus)
                .orderByDesc(SysRole::getId);
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(item -> item.like(SysRole::getRoleCode, kw)
                    .or()
                    .like(SysRole::getRoleName, kw)
                    .or()
                    .like(SysRole::getDescription, kw));
        }
        return roleMapper.selectList(wrapper);
    }

    public SysRole getRole(Long roleId) {
        if (roleId == null) {
            throw new BizException("roleId 不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        return role;
    }

    public SysRole createRole(RoleCommand command) {
        validateRoleCommand(command, false);
        String roleCode = normalizeCode(command.roleCode());
        SysRole existed = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .last("LIMIT 1"));
        if (existed != null) {
            return existed;
        }

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(command.roleName().trim());
        role.setDescription(command.description());
        role.setStatus(normalizeStatus(command.status(), 1));
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        return role;
    }

    public SysRole updateRole(Long roleId, RoleCommand command) {
        SysRole role = getRole(roleId);
        validateRoleCommand(command, true);
        if (command.roleCode() != null && !command.roleCode().isBlank()) {
            String roleCode = normalizeCode(command.roleCode());
            SysRole existed = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, roleCode)
                    .ne(SysRole::getId, roleId)
                    .last("LIMIT 1"));
            if (existed != null) {
                throw new BizException("角色编码已存在");
            }
            role.setRoleCode(roleCode);
        }
        if (command.roleName() != null && !command.roleName().isBlank()) {
            role.setRoleName(command.roleName().trim());
        }
        if (command.description() != null) {
            role.setDescription(command.description());
        }
        if (command.status() != null) {
            role.setStatus(normalizeStatus(command.status(), role.getStatus()));
        }
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        return role;
    }

    public SysRole changeRoleStatus(Long roleId, Integer status) {
        SysRole role = getRole(roleId);
        if (SUPER_ADMIN.equalsIgnoreCase(role.getRoleCode()) && Integer.valueOf(0).equals(status)) {
            throw new BizException("SUPER_ADMIN 不能停用");
        }
        role.setStatus(normalizeStatus(status, 1));
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        return role;
    }

    public List<SysPermission> listPermissions(String type) {
        return listPermissions(type, null, null);
    }

    public List<SysPermission> listPermissions(String type, String keyword, Integer status) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(SysPermission::getPermissionType, type.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null) {
            wrapper.eq(SysPermission::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(item -> item.like(SysPermission::getPermissionCode, kw)
                    .or()
                    .like(SysPermission::getPermissionName, kw)
                    .or()
                    .like(SysPermission::getPath, kw)
                    .or()
                    .like(SysPermission::getComponent, kw));
        }
        return permissionMapper.selectList(wrapper);
    }

    public SysPermission getPermission(Long permissionId) {
        if (permissionId == null) {
            throw new BizException("permissionId 不能为空");
        }
        SysPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new BizException(404, "权限不存在");
        }
        return permission;
    }

    public SysPermission createPermission(PermissionCommand command) {
        validatePermissionCommand(command, false);
        String permissionCode = normalizePermissionCode(command.permissionCode());
        SysPermission existed = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, permissionCode)
                .last("LIMIT 1"));
        if (existed != null) {
            return existed;
        }

        SysPermission permission = new SysPermission();
        applyPermissionCommand(permission, command, false);
        permission.setPermissionCode(permissionCode);
        permission.setStatus(normalizeStatus(command.status(), 1));
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.insert(permission);
        return permission;
    }

    public SysPermission updatePermission(Long permissionId, PermissionCommand command) {
        SysPermission permission = getPermission(permissionId);
        validatePermissionCommand(command, true);
        if (command.permissionCode() != null && !command.permissionCode().isBlank()) {
            String permissionCode = normalizePermissionCode(command.permissionCode());
            SysPermission existed = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getPermissionCode, permissionCode)
                    .ne(SysPermission::getId, permissionId)
                    .last("LIMIT 1"));
            if (existed != null) {
                throw new BizException("权限编码已存在");
            }
            permission.setPermissionCode(permissionCode);
        }
        applyPermissionCommand(permission, command, true);
        if (command.status() != null) {
            permission.setStatus(normalizeStatus(command.status(), permission.getStatus()));
        }
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
        return permission;
    }

    public SysPermission changePermissionStatus(Long permissionId, Integer status) {
        SysPermission permission = getPermission(permissionId);
        if ("*".equals(permission.getPermissionCode()) && Integer.valueOf(0).equals(status)) {
            throw new BizException("通配权限不能停用");
        }
        permission.setStatus(normalizeStatus(status, 1));
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
        return permission;
    }

    public List<SysRole> userRoles(Long userId) {
        validateId(userId, "userId");
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .inSql(SysRole::getId, "SELECT role_id FROM sys_user_role WHERE user_id = " + userId)
                .orderByDesc(SysRole::getId));
    }

    @Transactional
    public List<SysRole> assignUserRoles(Long userId, AssignRolesCommand command) {
        validateId(userId, "userId");
        if (command != null && command.roleIds() != null) {
            for (Long roleId : command.roleIds()) {
                if (roleId == null) {
                    continue;
                }
                SysRole role = getRole(roleId);
                if (Integer.valueOf(0).equals(role.getStatus())) {
                    throw new BizException("不能分配已停用角色：" + role.getRoleName());
                }
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));

        if (command != null && command.roleIds() != null) {
            for (Long roleId : command.roleIds()) {
                if (roleId == null) {
                    continue;
                }
                SysUserRole relation = new SysUserRole();
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                userRoleMapper.insert(relation);
            }
        }
        return userRoles(userId);
    }

    public List<SysPermission> rolePermissions(Long roleId) {
        validateId(roleId, "roleId");
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .inSql(SysPermission::getId, "SELECT permission_id FROM sys_role_permission WHERE role_id = " + roleId)
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));
    }

    public RolePermissionSummaryView rolePermissionSummary(Long roleId) {
        SysRole role = getRole(roleId);
        List<SysPermission> permissions = rolePermissions(roleId);
        List<Long> permissionIds = permissions.stream().map(SysPermission::getId).toList();
        return new RolePermissionSummaryView(role, permissions, permissionIds, permissions.size());
    }

    @Transactional
    public List<SysPermission> assignRolePermissions(Long roleId, AssignPermissionsCommand command) {
        SysRole role = getRole(roleId);
        if (Integer.valueOf(0).equals(role.getStatus())) {
            throw new BizException("不能给已停用角色分配权限");
        }
        if (command != null && command.permissionIds() != null) {
            for (Long permissionId : command.permissionIds()) {
                if (permissionId == null) {
                    continue;
                }
                SysPermission permission = getPermission(permissionId);
                if (Integer.valueOf(0).equals(permission.getStatus())) {
                    throw new BizException("不能分配已停用权限：" + permission.getPermissionName());
                }
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));

        if (command != null && command.permissionIds() != null) {
            for (Long permissionId : command.permissionIds()) {
                if (permissionId == null) {
                    continue;
                }
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(roleId);
                relation.setPermissionId(permissionId);
                rolePermissionMapper.insert(relation);
            }
        }
        return rolePermissions(roleId);
    }

    public UserPermissionView permissionView(Long userId) {
        validateId(userId, "userId");
        List<SysRole> roles = userRoles(userId);

        String permissionSql = "SELECT DISTINCT rp.permission_id " +
                "FROM sys_role_permission rp " +
                "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
                "JOIN sys_role r ON r.id = ur.role_id " +
                "WHERE ur.user_id = " + userId + " AND COALESCE(r.status, 1) = 1";

        List<SysPermission> allPermissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .inSql(SysPermission::getId, permissionSql)
                .eq(SysPermission::getStatus, 1)
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));

        List<SysPermission> menus = allPermissions.stream()
                .filter(item -> "MENU".equals(item.getPermissionType()))
                .toList();

        List<String> codes = allPermissions.stream()
                .map(SysPermission::getPermissionCode)
                .distinct()
                .toList();

        return new UserPermissionView(userId, roles, menus, codes);
    }

    private void validateRoleCommand(RoleCommand command, boolean partial) {
        if (command == null) {
            throw new BizException("请求体不能为空");
        }
        if (!partial && (command.roleCode() == null || command.roleCode().isBlank())) {
            throw new BizException("角色编码不能为空");
        }
        if (!partial && (command.roleName() == null || command.roleName().isBlank())) {
            throw new BizException("角色名称不能为空");
        }
        if (command.roleCode() != null && !command.roleCode().isBlank()) {
            String code = command.roleCode().trim();
            if (!code.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
                throw new BizException("角色编码必须为大写字母、数字、下划线，且以字母开头");
            }
        }
    }

    private void validatePermissionCommand(PermissionCommand command, boolean partial) {
        if (command == null) {
            throw new BizException("请求体不能为空");
        }
        if (!partial && (command.permissionCode() == null || command.permissionCode().isBlank())) {
            throw new BizException("权限编码不能为空");
        }
        if (!partial && (command.permissionName() == null || command.permissionName().isBlank())) {
            throw new BizException("权限名称不能为空");
        }
        if (command.permissionCode() != null && !command.permissionCode().isBlank()) {
            String code = command.permissionCode().trim();
            if (!"*".equals(code) && !code.matches("^[a-z][a-z0-9]*(?::[a-z][a-z0-9]*){1,4}$")) {
                throw new BizException("权限编码建议使用 resource:action 格式，例如 video:transcode:view");
            }
        }
        if (command.permissionType() != null && !command.permissionType().isBlank()) {
            String type = command.permissionType().trim().toUpperCase(Locale.ROOT);
            if (!List.of("MENU", "BUTTON", "API").contains(type)) {
                throw new BizException("权限类型只允许 MENU/BUTTON/API");
            }
        }
    }

    private void applyPermissionCommand(SysPermission permission, PermissionCommand command, boolean partial) {
        if (!partial || command.parentId() != null) {
            permission.setParentId(command.parentId() == null ? 0L : command.parentId());
        }
        if (command.permissionName() != null && !command.permissionName().isBlank()) {
            permission.setPermissionName(command.permissionName().trim());
        }
        if (!partial || command.permissionType() != null) {
            permission.setPermissionType(command.permissionType() == null || command.permissionType().isBlank()
                    ? "MENU"
                    : command.permissionType().trim().toUpperCase(Locale.ROOT));
        }
        if (!partial || command.path() != null) {
            permission.setPath(command.path());
        }
        if (!partial || command.component() != null) {
            permission.setComponent(command.component());
        }
        if (!partial || command.icon() != null) {
            permission.setIcon(command.icon());
        }
        if (!partial || command.sortNo() != null) {
            permission.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        }
    }

    private void validateId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BizException(name + " 不能为空");
        }
    }

    private Integer normalizeStatus(Integer status, Integer defaultValue) {
        Integer value = status == null ? defaultValue : status;
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePermissionCode(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
