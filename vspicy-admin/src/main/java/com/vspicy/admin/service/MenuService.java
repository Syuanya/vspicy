package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.MenuCommand;
import com.vspicy.admin.dto.MenuOverviewView;
import com.vspicy.admin.dto.MenuView;
import com.vspicy.admin.dto.RoleMenuAssignCommand;
import com.vspicy.admin.dto.RoleMenuSummaryView;
import com.vspicy.admin.entity.SysMenu;
import com.vspicy.admin.entity.SysRole;
import com.vspicy.admin.entity.SysRoleMenu;
import com.vspicy.admin.mapper.SysMenuMapper;
import com.vspicy.admin.mapper.SysRoleMapper;
import com.vspicy.admin.mapper.SysRoleMenuMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MenuService {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_.:-]{2,127}$");
    private static final Set<String> MENU_TYPES = Set.of("DIR", "MENU", "BUTTON");

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMapper roleMapper;

    public MenuService(SysMenuMapper menuMapper, SysRoleMenuMapper roleMenuMapper, SysRoleMapper roleMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleMapper = roleMapper;
    }

    public List<MenuView> tree(String keyword, Integer status, Boolean visible) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSortNo)
                .orderByAsc(SysMenu::getId);
        if (status != null) {
            wrapper.eq(SysMenu::getStatus, normalizeStatus(status));
        }
        if (visible != null) {
            wrapper.eq(SysMenu::getVisible, visible ? 1 : 0);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(SysMenu::getMenuCode, value)
                    .or().like(SysMenu::getMenuName, value)
                    .or().like(SysMenu::getPath, value)
                    .or().like(SysMenu::getPermissionCode, value)
                    .or().like(SysMenu::getRemark, value));
        }
        List<SysMenu> menus = menuMapper.selectList(wrapper);
        return buildTree(menus);
    }

    public MenuOverviewView overview() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>());
        long dir = 0;
        long menu = 0;
        long button = 0;
        long visible = 0;
        long hidden = 0;
        long enabled = 0;
        long disabled = 0;
        for (SysMenu item : menus) {
            String type = normalizeType(item.getMenuType());
            if ("DIR".equals(type)) dir++;
            else if ("BUTTON".equals(type)) button++;
            else menu++;
            if (Integer.valueOf(1).equals(item.getVisible())) visible++;
            else hidden++;
            if (Integer.valueOf(1).equals(item.getStatus())) enabled++;
            else disabled++;
        }
        return new MenuOverviewView(menus.size(), dir, menu, button, visible, hidden, enabled, disabled);
    }

    public MenuView get(Long id) {
        return toView(mustGet(id), List.of());
    }

    @Transactional
    public MenuView create(MenuCommand command) {
        validate(command, true, null);
        SysMenu menu = new SysMenu();
        apply(menu, command, true);
        menuMapper.insert(menu);
        return get(menu.getId());
    }

    @Transactional
    public MenuView update(Long id, MenuCommand command) {
        SysMenu menu = mustGet(id);
        if (!isEditable(menu)) {
            throw new BizException("系统内置菜单不允许编辑");
        }
        validate(command, false, id);
        apply(menu, command, false);
        if (menu.getParentId() != null && menu.getParentId().equals(id)) {
            throw new BizException("父级菜单不能选择自身");
        }
        menuMapper.updateById(menu);
        return get(id);
    }

    @Transactional
    public MenuView enable(Long id) {
        SysMenu menu = mustGet(id);
        menu.setStatus(1);
        menuMapper.updateById(menu);
        return get(id);
    }

    @Transactional
    public MenuView disable(Long id) {
        SysMenu menu = mustGet(id);
        if (!isEditable(menu)) {
            throw new BizException("系统内置菜单不允许停用");
        }
        menu.setStatus(0);
        menuMapper.updateById(menu);
        return get(id);
    }

    @Transactional
    public MenuView show(Long id) {
        SysMenu menu = mustGet(id);
        menu.setVisible(1);
        menuMapper.updateById(menu);
        return get(id);
    }

    @Transactional
    public MenuView hide(Long id) {
        SysMenu menu = mustGet(id);
        menu.setVisible(0);
        menuMapper.updateById(menu);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysMenu menu = mustGet(id);
        if (!isEditable(menu)) {
            throw new BizException("系统内置菜单不允许删除");
        }
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (count != null && count > 0) {
            throw new BizException("存在子菜单，不能删除");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
    }

    public RoleMenuSummaryView roleMenus(Long roleId) {
        SysRole role = mustGetRole(roleId);
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
        List<SysMenu> menus = menuIds.isEmpty()
                ? List.of()
                : menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().in(SysMenu::getId, menuIds)
                .orderByAsc(SysMenu::getParentId).orderByAsc(SysMenu::getSortNo));
        return new RoleMenuSummaryView(role.getId(), role.getRoleCode(), role.getRoleName(), menuIds, buildTree(menus));
    }

    @Transactional
    public RoleMenuSummaryView assignRoleMenus(Long roleId, RoleMenuAssignCommand command) {
        SysRole role = mustGetRole(roleId);
        if (Integer.valueOf(0).equals(role.getStatus())) {
            throw new BizException("停用角色不能分配菜单");
        }
        List<Long> requestedIds = command == null || command.menuIds() == null ? List.of() : command.menuIds();
        List<Long> menuIds = requestedIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (!menuIds.isEmpty()) {
            List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().in(SysMenu::getId, menuIds));
            if (menus.size() != menuIds.size()) {
                throw new BizException("存在无效菜单 ID");
            }
            boolean hasDisabled = menus.stream().anyMatch(item -> Integer.valueOf(0).equals(item.getStatus()));
            if (hasDisabled) {
                throw new BizException("停用菜单不能分配给角色");
            }
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
        return roleMenus(roleId);
    }

    private void validate(MenuCommand command, boolean create, Long currentId) {
        if (command == null) {
            throw new BizException("菜单内容不能为空");
        }
        if (create || command.menuCode() != null) {
            if (command.menuCode() == null || command.menuCode().isBlank()) {
                throw new BizException("菜单编码不能为空");
            }
            String code = command.menuCode().trim();
            if (!CODE_PATTERN.matcher(code).matches()) {
                throw new BizException("菜单编码只能包含小写字母、数字、下划线、点、冒号和短横线，且长度为 3-128");
            }
            LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getMenuCode, code);
            if (currentId != null) wrapper.ne(SysMenu::getId, currentId);
            SysMenu existed = menuMapper.selectOne(wrapper.last("LIMIT 1"));
            if (existed != null) {
                throw new BizException("菜单编码已存在");
            }
        }
        if (create || command.menuName() != null) {
            if (command.menuName() == null || command.menuName().isBlank()) {
                throw new BizException("菜单名称不能为空");
            }
        }
        if (create || command.menuType() != null) {
            String type = normalizeType(command.menuType());
            if (!MENU_TYPES.contains(type)) {
                throw new BizException("菜单类型只能是 DIR/MENU/BUTTON");
            }
        }
        if (command.parentId() != null && command.parentId() > 0) {
            SysMenu parent = menuMapper.selectById(command.parentId());
            if (parent == null) {
                throw new BizException("父级菜单不存在");
            }
            if (currentId != null && command.parentId().equals(currentId)) {
                throw new BizException("父级菜单不能选择自身");
            }
        }
    }

    private void apply(SysMenu menu, MenuCommand command, boolean create) {
        if (create || command.parentId() != null) {
            menu.setParentId(command.parentId() == null || command.parentId() <= 0 ? 0L : command.parentId());
        }
        if (create || command.menuCode() != null) menu.setMenuCode(command.menuCode().trim());
        if (create || command.menuName() != null) menu.setMenuName(command.menuName().trim());
        if (create || command.menuType() != null) menu.setMenuType(normalizeType(command.menuType()));
        if (create || command.path() != null) menu.setPath(trimToEmpty(command.path()));
        if (create || command.component() != null) menu.setComponent(trimToEmpty(command.component()));
        if (create || command.icon() != null) menu.setIcon(trimToEmpty(command.icon()));
        if (create || command.permissionCode() != null) menu.setPermissionCode(trimToEmpty(command.permissionCode()));
        if (create || command.sortNo() != null) menu.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        if (create || command.visible() != null) menu.setVisible(Boolean.FALSE.equals(command.visible()) ? 0 : 1);
        if (create || command.status() != null) menu.setStatus(command.status() == null ? 1 : normalizeStatus(command.status()));
        if (create || command.editable() != null) menu.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        if (create || command.remark() != null) menu.setRemark(command.remark());
    }

    private List<MenuView> buildTree(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> children = menus.stream()
                .collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId(), LinkedHashMap::new, Collectors.toList()));
        Set<Long> idSet = menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        List<SysMenu> roots = menus.stream()
                .filter(item -> item.getParentId() == null || item.getParentId() == 0 || !idSet.contains(item.getParentId()))
                .sorted(menuComparator())
                .toList();
        return roots.stream().map(item -> toTreeView(item, children, new HashSet<>())).toList();
    }

    private MenuView toTreeView(SysMenu menu, Map<Long, List<SysMenu>> children, Set<Long> path) {
        if (path.contains(menu.getId())) {
            return toView(menu, List.of());
        }
        path.add(menu.getId());
        List<MenuView> childViews = children.getOrDefault(menu.getId(), List.of())
                .stream()
                .sorted(menuComparator())
                .map(child -> toTreeView(child, children, new HashSet<>(path)))
                .toList();
        return toView(menu, childViews);
    }

    private Comparator<SysMenu> menuComparator() {
        return Comparator.comparing((SysMenu item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
    }

    private MenuView toView(SysMenu menu, List<MenuView> children) {
        return new MenuView(
                menu.getId(),
                menu.getParentId() == null ? 0L : menu.getParentId(),
                menu.getMenuCode(),
                menu.getMenuName(),
                normalizeType(menu.getMenuType()),
                menu.getPath(),
                menu.getComponent(),
                menu.getIcon(),
                menu.getPermissionCode(),
                menu.getSortNo(),
                Integer.valueOf(1).equals(menu.getVisible()),
                menu.getStatus(),
                isEditable(menu),
                menu.getRemark(),
                menu.getCreatedAt(),
                menu.getUpdatedAt(),
                children == null ? List.of() : children
        );
    }

    private SysMenu mustGet(Long id) {
        if (id == null || id <= 0) {
            throw new BizException("菜单 ID 不能为空");
        }
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(404, "菜单不存在");
        }
        return menu;
    }

    private SysRole mustGetRole(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new BizException("角色 ID 不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        return role;
    }

    private String normalizeType(String value) {
        if (value == null || value.isBlank()) return "MENU";
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private boolean isEditable(SysMenu menu) {
        return !Integer.valueOf(0).equals(menu.getEditable());
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
