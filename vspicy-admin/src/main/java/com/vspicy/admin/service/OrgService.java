package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.DeptCommand;
import com.vspicy.admin.dto.DeptView;
import com.vspicy.admin.dto.OrgOverviewView;
import com.vspicy.admin.dto.PostCommand;
import com.vspicy.admin.dto.PostView;
import com.vspicy.admin.dto.UserOrgAssignCommand;
import com.vspicy.admin.dto.UserOrgView;
import com.vspicy.admin.entity.SysDept;
import com.vspicy.admin.entity.SysPost;
import com.vspicy.admin.entity.SysUserDept;
import com.vspicy.admin.entity.SysUserPost;
import com.vspicy.admin.mapper.SysDeptMapper;
import com.vspicy.admin.mapper.SysPostMapper;
import com.vspicy.admin.mapper.SysUserDeptMapper;
import com.vspicy.admin.mapper.SysUserPostMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OrgService {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{1,63}$");

    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysUserDeptMapper userDeptMapper;
    private final SysUserPostMapper userPostMapper;

    public OrgService(SysDeptMapper deptMapper,
                      SysPostMapper postMapper,
                      SysUserDeptMapper userDeptMapper,
                      SysUserPostMapper userPostMapper) {
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.userDeptMapper = userDeptMapper;
        this.userPostMapper = userPostMapper;
    }

    public OrgOverviewView overview() {
        long deptCount = safeCount(deptMapper.selectCount(new LambdaQueryWrapper<>()));
        long enabledDeptCount = safeCount(deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1)));
        long disabledDeptCount = deptCount - enabledDeptCount;
        long postCount = safeCount(postMapper.selectCount(new LambdaQueryWrapper<>()));
        long enabledPostCount = safeCount(postMapper.selectCount(new LambdaQueryWrapper<SysPost>().eq(SysPost::getStatus, 1)));
        long disabledPostCount = postCount - enabledPostCount;
        long userDeptCount = safeCount(userDeptMapper.selectCount(new LambdaQueryWrapper<>()));
        long userPostCount = safeCount(userPostMapper.selectCount(new LambdaQueryWrapper<>()));
        return new OrgOverviewView(deptCount, enabledDeptCount, disabledDeptCount, postCount, enabledPostCount,
                disabledPostCount, userDeptCount, userPostCount);
    }

    public List<DeptView> deptTree(String keyword, Integer status) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getParentId)
                .orderByAsc(SysDept::getSortNo)
                .orderByAsc(SysDept::getId);
        if (status != null) {
            wrapper.eq(SysDept::getStatus, normalizeStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item.like(SysDept::getDeptCode, value)
                    .or().like(SysDept::getDeptName, value)
                    .or().like(SysDept::getLeaderName, value)
                    .or().like(SysDept::getLeaderPhone, value)
                    .or().like(SysDept::getRemark, value));
        }
        return buildDeptTree(deptMapper.selectList(wrapper));
    }

    public DeptView getDept(Long id) {
        return toDeptView(mustGetDept(id), List.of());
    }

    @Transactional
    public DeptView createDept(DeptCommand command) {
        validateDept(command, true, null);
        SysDept dept = new SysDept();
        applyDept(dept, command, true);
        deptMapper.insert(dept);
        return getDept(dept.getId());
    }

    @Transactional
    public DeptView updateDept(Long id, DeptCommand command) {
        SysDept dept = mustGetDept(id);
        if (!isEditable(dept.getEditable())) {
            throw new BizException("系统内置部门不允许编辑");
        }
        validateDept(command, false, id);
        applyDept(dept, command, false);
        if (dept.getParentId() != null && dept.getParentId().equals(id)) {
            throw new BizException("父级部门不能选择自身");
        }
        deptMapper.updateById(dept);
        return getDept(id);
    }

    @Transactional
    public DeptView enableDept(Long id) {
        SysDept dept = mustGetDept(id);
        dept.setStatus(1);
        deptMapper.updateById(dept);
        return getDept(id);
    }

    @Transactional
    public DeptView disableDept(Long id) {
        SysDept dept = mustGetDept(id);
        if (!isEditable(dept.getEditable())) {
            throw new BizException("系统内置部门不允许停用");
        }
        dept.setStatus(0);
        deptMapper.updateById(dept);
        return getDept(id);
    }

    @Transactional
    public void deleteDept(Long id) {
        SysDept dept = mustGetDept(id);
        if (!isEditable(dept.getEditable())) {
            throw new BizException("系统内置部门不允许删除");
        }
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (safeCount(childCount) > 0) {
            throw new BizException("存在子部门，不能删除");
        }
        Long userCount = userDeptMapper.selectCount(new LambdaQueryWrapper<SysUserDept>().eq(SysUserDept::getDeptId, id));
        if (safeCount(userCount) > 0) {
            throw new BizException("部门已关联用户，不能删除");
        }
        deptMapper.deleteById(id);
    }

    public List<PostView> posts(String keyword, Integer status) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .orderByAsc(SysPost::getSortNo)
                .orderByAsc(SysPost::getId);
        if (status != null) {
            wrapper.eq(SysPost::getStatus, normalizeStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item.like(SysPost::getPostCode, value)
                    .or().like(SysPost::getPostName, value)
                    .or().like(SysPost::getRemark, value));
        }
        return postMapper.selectList(wrapper).stream().map(this::toPostView).toList();
    }

    public PostView getPost(Long id) {
        return toPostView(mustGetPost(id));
    }

    @Transactional
    public PostView createPost(PostCommand command) {
        validatePost(command, true, null);
        SysPost post = new SysPost();
        applyPost(post, command, true);
        postMapper.insert(post);
        return getPost(post.getId());
    }

    @Transactional
    public PostView updatePost(Long id, PostCommand command) {
        SysPost post = mustGetPost(id);
        if (!isEditable(post.getEditable())) {
            throw new BizException("系统内置岗位不允许编辑");
        }
        validatePost(command, false, id);
        applyPost(post, command, false);
        postMapper.updateById(post);
        return getPost(id);
    }

    @Transactional
    public PostView enablePost(Long id) {
        SysPost post = mustGetPost(id);
        post.setStatus(1);
        postMapper.updateById(post);
        return getPost(id);
    }

    @Transactional
    public PostView disablePost(Long id) {
        SysPost post = mustGetPost(id);
        if (!isEditable(post.getEditable())) {
            throw new BizException("系统内置岗位不允许停用");
        }
        post.setStatus(0);
        postMapper.updateById(post);
        return getPost(id);
    }

    @Transactional
    public void deletePost(Long id) {
        SysPost post = mustGetPost(id);
        if (!isEditable(post.getEditable())) {
            throw new BizException("系统内置岗位不允许删除");
        }
        Long userCount = userPostMapper.selectCount(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getPostId, id));
        if (safeCount(userCount) > 0) {
            throw new BizException("岗位已关联用户，不能删除");
        }
        postMapper.deleteById(id);
    }

    public UserOrgView userOrg(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("用户 ID 无效");
        }
        List<SysUserDept> userDepts = userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>().eq(SysUserDept::getUserId, userId));
        List<SysUserPost> userPosts = userPostMapper.selectList(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, userId));
        List<Long> deptIds = userDepts.stream().map(SysUserDept::getDeptId).toList();
        List<Long> postIds = userPosts.stream().map(SysUserPost::getPostId).toList();
        Long primaryDeptId = userDepts.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getPrimaryFlag()))
                .map(SysUserDept::getDeptId)
                .findFirst()
                .orElse(deptIds.isEmpty() ? null : deptIds.get(0));
        List<DeptView> depts = deptIds.isEmpty() ? List.of()
                : deptMapper.selectList(new LambdaQueryWrapper<SysDept>().in(SysDept::getId, deptIds))
                .stream().map(item -> toDeptView(item, List.of())).toList();
        List<PostView> posts = postIds.isEmpty() ? List.of()
                : postMapper.selectList(new LambdaQueryWrapper<SysPost>().in(SysPost::getId, postIds))
                .stream().map(this::toPostView).toList();
        return new UserOrgView(userId, primaryDeptId, deptIds, postIds, depts, posts);
    }

    @Transactional
    public UserOrgView assignUserOrg(Long userId, UserOrgAssignCommand command) {
        if (userId == null || userId <= 0) {
            throw new BizException("用户 ID 无效");
        }
        List<Long> deptIds = distinctIds(command == null ? null : command.deptIds());
        List<Long> postIds = distinctIds(command == null ? null : command.postIds());
        Long primaryDeptId = command == null ? null : command.primaryDeptId();
        if (primaryDeptId != null && primaryDeptId > 0 && !deptIds.contains(primaryDeptId)) {
            deptIds = new ArrayList<>(deptIds);
            deptIds.add(primaryDeptId);
        }
        validateDeptIds(deptIds);
        validatePostIds(postIds);
        userDeptMapper.delete(new LambdaQueryWrapper<SysUserDept>().eq(SysUserDept::getUserId, userId));
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, userId));
        for (Long deptId : deptIds) {
            SysUserDept relation = new SysUserDept();
            relation.setUserId(userId);
            relation.setDeptId(deptId);
            relation.setPrimaryFlag(deptId.equals(primaryDeptId) || primaryDeptId == null && deptIds.indexOf(deptId) == 0 ? 1 : 0);
            userDeptMapper.insert(relation);
        }
        for (Long postId : postIds) {
            SysUserPost relation = new SysUserPost();
            relation.setUserId(userId);
            relation.setPostId(postId);
            userPostMapper.insert(relation);
        }
        return userOrg(userId);
    }

    private void validateDept(DeptCommand command, boolean create, Long currentId) {
        if (command == null) throw new BizException("部门内容不能为空");
        if (create || command.deptCode() != null) {
            String code = command.deptCode() == null ? "" : command.deptCode().trim();
            if (!CODE_PATTERN.matcher(code).matches()) throw new BizException("部门编码格式不正确");
            LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeptCode, code);
            if (currentId != null) wrapper.ne(SysDept::getId, currentId);
            if (deptMapper.selectOne(wrapper.last("LIMIT 1")) != null) throw new BizException("部门编码已存在");
        }
        if (create || command.deptName() != null) {
            if (command.deptName() == null || command.deptName().isBlank()) throw new BizException("部门名称不能为空");
        }
        if (command.parentId() != null && command.parentId() > 0) {
            SysDept parent = deptMapper.selectById(command.parentId());
            if (parent == null) throw new BizException("父级部门不存在");
            if (currentId != null && command.parentId().equals(currentId)) throw new BizException("父级部门不能选择自身");
        }
    }

    private void validatePost(PostCommand command, boolean create, Long currentId) {
        if (command == null) throw new BizException("岗位内容不能为空");
        if (create || command.postCode() != null) {
            String code = command.postCode() == null ? "" : command.postCode().trim();
            if (!CODE_PATTERN.matcher(code).matches()) throw new BizException("岗位编码格式不正确");
            LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>().eq(SysPost::getPostCode, code);
            if (currentId != null) wrapper.ne(SysPost::getId, currentId);
            if (postMapper.selectOne(wrapper.last("LIMIT 1")) != null) throw new BizException("岗位编码已存在");
        }
        if (create || command.postName() != null) {
            if (command.postName() == null || command.postName().isBlank()) throw new BizException("岗位名称不能为空");
        }
    }

    private void applyDept(SysDept dept, DeptCommand command, boolean create) {
        if (create || command.parentId() != null) dept.setParentId(command.parentId() == null || command.parentId() <= 0 ? 0L : command.parentId());
        if (create || command.deptCode() != null) dept.setDeptCode(command.deptCode().trim());
        if (create || command.deptName() != null) dept.setDeptName(command.deptName().trim());
        if (create || command.leaderName() != null) dept.setLeaderName(blankToNull(command.leaderName()));
        if (create || command.leaderPhone() != null) dept.setLeaderPhone(blankToNull(command.leaderPhone()));
        if (create || command.sortNo() != null) dept.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        if (create || command.status() != null) dept.setStatus(normalizeStatus(command.status()));
        if (create || command.editable() != null) dept.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        if (create || command.remark() != null) dept.setRemark(blankToNull(command.remark()));
    }

    private void applyPost(SysPost post, PostCommand command, boolean create) {
        if (create || command.postCode() != null) post.setPostCode(command.postCode().trim());
        if (create || command.postName() != null) post.setPostName(command.postName().trim());
        if (create || command.sortNo() != null) post.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        if (create || command.status() != null) post.setStatus(normalizeStatus(command.status()));
        if (create || command.editable() != null) post.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        if (create || command.remark() != null) post.setRemark(blankToNull(command.remark()));
    }

    private void validateDeptIds(List<Long> deptIds) {
        if (deptIds.isEmpty()) return;
        List<SysDept> rows = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().in(SysDept::getId, deptIds));
        if (rows.size() != deptIds.size()) throw new BizException("存在无效部门 ID");
        if (rows.stream().anyMatch(item -> Integer.valueOf(0).equals(item.getStatus()))) throw new BizException("停用部门不能分配给用户");
    }

    private void validatePostIds(List<Long> postIds) {
        if (postIds.isEmpty()) return;
        List<SysPost> rows = postMapper.selectList(new LambdaQueryWrapper<SysPost>().in(SysPost::getId, postIds));
        if (rows.size() != postIds.size()) throw new BizException("存在无效岗位 ID");
        if (rows.stream().anyMatch(item -> Integer.valueOf(0).equals(item.getStatus()))) throw new BizException("停用岗位不能分配给用户");
    }

    private List<DeptView> buildDeptTree(List<SysDept> rows) {
        Map<Long, List<SysDept>> children = rows.stream().collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId(), LinkedHashMap::new, Collectors.toList()));
        Comparator<SysDept> comparator = Comparator.comparing((SysDept item) -> item.getSortNo() == null ? 0 : item.getSortNo()).thenComparing(SysDept::getId);
        children.values().forEach(list -> list.sort(comparator));
        return buildDeptChildren(0L, children);
    }

    private List<DeptView> buildDeptChildren(Long parentId, Map<Long, List<SysDept>> children) {
        return children.getOrDefault(parentId, List.of()).stream()
                .map(item -> toDeptView(item, buildDeptChildren(item.getId(), children)))
                .toList();
    }

    private DeptView toDeptView(SysDept item, List<DeptView> children) {
        return new DeptView(item.getId(), item.getParentId(), item.getDeptCode(), item.getDeptName(), item.getLeaderName(),
                item.getLeaderPhone(), item.getSortNo(), item.getStatus(), isEditable(item.getEditable()), item.getRemark(),
                item.getCreatedAt(), item.getUpdatedAt(), children == null ? List.of() : children);
    }

    private PostView toPostView(SysPost item) {
        return new PostView(item.getId(), item.getPostCode(), item.getPostName(), item.getSortNo(), item.getStatus(),
                isEditable(item.getEditable()), item.getRemark(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private SysDept mustGetDept(Long id) {
        if (id == null || id <= 0) throw new BizException("部门 ID 无效");
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) throw new BizException("部门不存在");
        return dept;
    }

    private SysPost mustGetPost(Long id) {
        if (id == null || id <= 0) throw new BizException("岗位 ID 无效");
        SysPost post = postMapper.selectById(id);
        if (post == null) throw new BizException("岗位不存在");
        return post;
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null) return List.of();
        Set<Long> seen = new HashSet<>();
        List<Long> result = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && id > 0 && seen.add(id)) result.add(id);
        }
        return result;
    }

    private Integer normalizeStatus(Integer status) {
        return Integer.valueOf(0).equals(status) ? 0 : 1;
    }

    private boolean isEditable(Integer editable) {
        return !Integer.valueOf(0).equals(editable);
    }

    private long safeCount(Long value) {
        return value == null ? 0 : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
