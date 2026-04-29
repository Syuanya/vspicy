package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.*;
import com.vspicy.admin.entity.*;
import com.vspicy.admin.mapper.*;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminPermissionService {
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

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getId));
    }

    public SysRole createRole(RoleCommand command) {
        if (command == null || command.roleCode() == null || command.roleCode().isBlank()) {
            throw new BizException("角色编码不能为空");
        }
        if (command.roleName() == null || command.roleName().isBlank()) {
            throw new BizException("角色名称不能为空");
        }

        SysRole existed = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, command.roleCode())
                .last("LIMIT 1"));
        if (existed != null) {
            return existed;
        }

        SysRole role = new SysRole();
        role.setRoleCode(command.roleCode());
        role.setRoleName(command.roleName());
        role.setDescription(command.description());
        role.setStatus(1);
        roleMapper.insert(role);
        return role;
    }

    public List<SysPermission> listPermissions(String type) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(SysPermission::getPermissionType, type);
        }
        return permissionMapper.selectList(wrapper);
    }

    public SysPermission createPermission(PermissionCommand command) {
        if (command == null || command.permissionCode() == null || command.permissionCode().isBlank()) {
            throw new BizException("权限编码不能为空");
        }
        if (command.permissionName() == null || command.permissionName().isBlank()) {
            throw new BizException("权限名称不能为空");
        }

        SysPermission existed = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, command.permissionCode())
                .last("LIMIT 1"));
        if (existed != null) {
            return existed;
        }

        SysPermission permission = new SysPermission();
        permission.setParentId(command.parentId() == null ? 0L : command.parentId());
        permission.setPermissionCode(command.permissionCode());
        permission.setPermissionName(command.permissionName());
        permission.setPermissionType(command.permissionType() == null || command.permissionType().isBlank() ? "MENU" : command.permissionType());
        permission.setPath(command.path());
        permission.setComponent(command.component());
        permission.setIcon(command.icon());
        permission.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        permission.setStatus(1);
        permissionMapper.insert(permission);
        return permission;
    }

    public List<SysRole> userRoles(Long userId) {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .inSql(SysRole::getId, "SELECT role_id FROM sys_user_role WHERE user_id = " + userId)
                .orderByDesc(SysRole::getId));
    }

    @Transactional
    public List<SysRole> assignUserRoles(Long userId, AssignRolesCommand command) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
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
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .inSql(SysPermission::getId, "SELECT permission_id FROM sys_role_permission WHERE role_id = " + roleId)
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));
    }

    @Transactional
    public List<SysPermission> assignRolePermissions(Long roleId, AssignPermissionsCommand command) {
        if (roleId == null) {
            throw new BizException("roleId 不能为空");
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
        List<SysRole> roles = userRoles(userId);

        String permissionSql = "SELECT DISTINCT rp.permission_id " +
                "FROM sys_role_permission rp " +
                "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
                "WHERE ur.user_id = " + userId;

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
}
