package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.PasswordUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.UserDto;
import com.cupk.aiacademy.dto.UserProfileDeleteRequest;
import com.cupk.aiacademy.dto.UserProfileUpdateRequest;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/me")
    public Result currentProfile() {
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            return Result.error("404", "用户不存在");
        }
        fillRole(user);
        return Result.success(user);
    }

    @PutMapping("/me")
    @Transactional(rollbackFor = Exception.class)
    public Result updateCurrentProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            return Result.error("404", "用户不存在");
        }

        String newPassword = trimToNull(request.getNewPassword());
        if (newPassword != null) {
            if (!PasswordUtils.matches(request.getCurrentPassword(), user.getPassword())) {
                return Result.error("400", "当前密码不正确");
            }
            user.setPassword(PasswordUtils.encode(newPassword));
        }

        String nickname = trimToNull(request.getNickname());
        user.setNickname(nickname == null ? user.getUsername() : nickname);
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setAvatar(trimToNull(request.getAvatar()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        fillRole(user);
        return Result.success(user, "个人信息已更新");
    }

    @DeleteMapping("/me")
    @Transactional(rollbackFor = Exception.class)
    public Result deactivateCurrentAccount(@Valid @RequestBody UserProfileDeleteRequest request) {
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            return Result.error("404", "用户不存在");
        }
        if (!PasswordUtils.matches(request.getCurrentPassword(), user.getPassword())) {
            return Result.error("400", "当前密码不正确");
        }
        if ("admin".equals(loginUser.getRoleCode()) && isLastActiveAdministrator()) {
            return Result.error("409", "至少需要保留一个有效管理员账号");
        }

        user.setStatus(0);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return Result.success(null, "账号已注销");
    }

    @GetMapping
    public Result pages(@RequestParam(defaultValue = "") String username,
                        @RequestParam(defaultValue = "") String keyword,
                        @RequestParam(required = false) String roleCode,
                        @RequestParam(required = false) Integer status,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<User> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String term = keyword.trim();
            queryWrapper.and(wrapper -> wrapper.like("username", term)
                    .or()
                    .like("nickname", term)
                    .or()
                    .like("email", term)
                    .or()
                    .like("phone", term));
        } else if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if ("admin".equals(loginUser.getRoleCode())) {
            if (!applyRoleFilter(page, queryWrapper, roleCode)) {
                return Result.success(page);
            }
        } else if ("teacher".equals(loginUser.getRoleCode())) {
            if (roleCode != null && !roleCode.trim().isEmpty() && !"student".equals(roleCode.trim().toLowerCase())) {
                page.setRecords(java.util.List.of());
                page.setTotal(0);
                return Result.success(page);
            }
            if (!applyRoleFilter(page, queryWrapper, "student")) {
                return Result.success(page);
            }
        } else {
            queryWrapper.eq("id", loginUser.getUserId());
        }
        queryWrapper.orderByDesc("id");
        userMapper.selectPage(page, queryWrapper);
        page.getRecords().forEach(this::fillRole);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }
        fillRole(user);
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        boolean allowed = "admin".equals(loginUser.getRoleCode())
                || ("teacher".equals(loginUser.getRoleCode()) && "student".equals(user.getRoleCode()))
                || ("student".equals(loginUser.getRoleCode()) && loginUser.getUserId().equals(id));
        if (!allowed) {
            return Result.error("403", "无权限查看该用户");
        }
        return Result.success(user);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result add(@Valid @RequestBody UserDto request) {
        permissionService.requireAdmin();
        User user = request.toEntity();
        applyRoleCode(user, request.getRoleCode());
        Result checkResult = checkUser(user, false);
        if (checkResult != null) {
            return checkResult;
        }
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", user.getUsername().trim())) != null) {
            return Result.error("400", "用户名已存在");
        }

        prepareNewUser(user);
        userMapper.insert(user);
        saveUserRole(user.getId(), getRoleId(user));
        return Result.success();
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result update(@PathVariable Integer id, @Valid @RequestBody UserDto request) {
        permissionService.requireAdmin();
        User user = request.toEntity();
        applyRoleCode(user, request.getRoleCode());
        User oldUser = userMapper.selectById(id);
        if (oldUser == null) {
            return Result.error("404", "用户不存在");
        }
        Result checkResult = checkUser(user, true);
        if (checkResult != null) {
            return checkResult;
        }

        User sameNameUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", user.getUsername().trim()));
        if (sameNameUser != null && !sameNameUser.getId().equals(id)) {
            return Result.error("400", "用户名已存在");
        }

        prepareUpdatedUser(user, oldUser, id);
        userMapper.updateById(user);
        if (user.getRoleId() != null) {
            saveUserRole(id, user.getRoleId());
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        permissionService.requireAdmin();
        if (userMapper.selectById(id) == null) {
            return Result.error("404", "用户不存在");
        }
        Long courseCount = courseMapper.selectCount(new QueryWrapper<Course>().eq("teacher_id", id));
        if (courseCount > 0) {
            return Result.error("409", "用户已被课程引用，不能删除");
        }
        int row = userMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkUser(User user, boolean update) {
        if (user == null) {
            return Result.error("400", "用户信息不能为空");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("400", "用户名不能为空");
        }
        if (!update && (user.getPassword() == null || user.getPassword().trim().isEmpty())) {
            return Result.error("400", "密码不能为空");
        }
        if (user.getRoleId() != null && roleMapper.selectById(user.getRoleId()) == null) {
            return Result.error("404", "角色不存在");
        }
        return null;
    }

    private void prepareNewUser(User user) {
        LocalDateTime now = LocalDateTime.now();
        user.setUsername(user.getUsername().trim());
        user.setPassword(PasswordUtils.encode(user.getPassword()));
        user.setNickname(normalizeNickname(user));
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
        user.setCreateTime(now);
        user.setUpdateTime(now);
    }

    private void prepareUpdatedUser(User user, User oldUser, Integer id) {
        user.setId(id);
        user.setUsername(user.getUsername().trim());
        user.setPassword(encodeOrKeepPassword(user.getPassword(), oldUser.getPassword()));
        user.setUpdateTime(LocalDateTime.now());
    }

    private String normalizeNickname(User user) {
        return user.getNickname() == null || user.getNickname().trim().isEmpty()
                ? user.getUsername()
                : user.getNickname().trim();
    }

    private String encodeOrKeepPassword(String password, String oldPassword) {
        if (password == null || password.trim().isEmpty()) {
            return oldPassword;
        }
        return password.equals(oldPassword) ? oldPassword : PasswordUtils.encode(password);
    }

    private Integer getRoleId(User user) {
        if (user.getRoleId() != null) {
            return user.getRoleId();
        }
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "student"));
        return role == null ? null : role.getId();
    }

    private boolean isLastActiveAdministrator() {
        Role adminRole = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "admin"));
        if (adminRole == null) {
            return true;
        }
        Long activeAdminCount = userMapper.selectCount(new QueryWrapper<User>()
                .eq("status", 1)
                .inSql("id", "select user_id from t_user_role where role_id = " + adminRole.getId()));
        return activeAdminCount == null || activeAdminCount <= 1;
    }

    private void applyRoleCode(User user, String roleCode) {
        if (user == null || roleCode == null || roleCode.trim().isEmpty() || user.getRoleId() != null) {
            return;
        }
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", roleCode.trim().toLowerCase()));
        if (role != null) {
            user.setRoleId(role.getId());
        }
    }

    private void saveUserRole(Integer userId, Integer roleId) {
        userRoleMapper.delete(new QueryWrapper<UserRole>().eq("user_id", userId));
        if (roleId != null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private void fillRole(User user) {
        UserRole userRole = userRoleMapper.selectList(new QueryWrapper<UserRole>().eq("user_id", user.getId())).stream().findFirst().orElse(null);
        if (userRole != null) {
            user.setRoleId(userRole.getRoleId());
            Role role = roleMapper.selectById(userRole.getRoleId());
            if (role != null) {
                user.setRoleCode(role.getRoleCode());
            }
        }
    }

    private boolean applyRoleFilter(Page<User> page, QueryWrapper<User> queryWrapper, String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return true;
        }
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", roleCode.trim().toLowerCase()));
        if (role == null) {
            page.setRecords(java.util.List.of());
            page.setTotal(0);
            return false;
        }
        queryWrapper.inSql("id", "select user_id from t_user_role where role_id = " + role.getId());
        return true;
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
