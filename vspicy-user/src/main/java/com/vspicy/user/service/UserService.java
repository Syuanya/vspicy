package com.vspicy.user.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.user.entity.SysUser;
import com.vspicy.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final SysUserMapper sysUserMapper;

    public UserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        user.setPasswordHash(null);
        return user;
    }

    public List<SysUser> listUsers(String keyword, Integer status, Integer userType, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
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
        users.forEach(user -> user.setPasswordHash(null));
        return users;
    }

    public SysUser updateStatus(Long userId, Integer status) {
        if (status == null || (status != 1 && status != 2 && status != 3)) {
            throw new BizException(400, "用户状态只能是 1=正常、2=禁用、3=注销");
        }
        SysUser user = getById(userId);
        user.setStatus(status);
        sysUserMapper.updateById(user);
        user.setPasswordHash(null);
        return user;
    }

    public SysUser updateUserType(Long userId, Integer userType) {
        if (userType == null || (userType != 1 && userType != 2 && userType != 9)) {
            throw new BizException(400, "用户类型只能是 1=普通用户、2=创作者、9=管理员");
        }
        SysUser user = getById(userId);
        user.setUserType(userType);
        sysUserMapper.updateById(user);
        user.setPasswordHash(null);
        return user;
    }
}
