package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.MD5Utils;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
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

    @GetMapping
    public ApiResponse<Page<User>> pages(@RequestParam(defaultValue = "") String username,
                        @RequestParam(required = false) Integer status,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<User> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("id");
        userMapper.selectPage(page, queryWrapper);
        page.getRecords().forEach(this::fillRole);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<User> selectById(@PathVariable Integer id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        fillRole(user);
        return ApiResponse.success(user);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> add(@RequestBody User user) {
        checkUser(user, false);
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", user.getUsername().trim())) != null) {
            throw new BusinessException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setUsername(user.getUsername().trim());
        user.setPassword(MD5Utils.md5(user.getPassword()));
        user.setNickname(user.getNickname() == null || user.getNickname().trim().isEmpty() ? user.getUsername() : user.getNickname().trim());
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        saveUserRole(user.getId(), getRoleId(user));
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody User user) {
        User oldUser = userMapper.selectById(id);
        if (oldUser == null) {
            throw new BusinessException("用户不存在");
        }
        checkUser(user, true);

        User sameNameUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", user.getUsername().trim()));
        if (sameNameUser != null && !sameNameUser.getId().equals(id)) {
            throw new BusinessException("用户名已存在");
        }

        user.setId(id);
        user.setUsername(user.getUsername().trim());
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            user.setPassword(oldUser.getPassword());
        } else if (!user.getPassword().equals(oldUser.getPassword())) {
            user.setPassword(MD5Utils.md5(user.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        if (user.getRoleId() != null) {
            saveUserRole(id, user.getRoleId());
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (userMapper.selectById(id) == null) {
            throw new BusinessException("用户不存在");
        }
        int row = userMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkUser(User user, boolean update) {
        if (user == null) {
            throw new BusinessException("用户信息不能为空");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (!update && (user.getPassword() == null || user.getPassword().trim().isEmpty())) {
            throw new BusinessException("密码不能为空");
        }
        if (user.getRoleId() != null && roleMapper.selectById(user.getRoleId()) == null) {
            throw new BusinessException("角色不存在");
        }
    }

    private Integer getRoleId(User user) {
        if (user.getRoleId() != null) {
            return user.getRoleId();
        }
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "student"));
        return role == null ? null : role.getId();
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
        UserRole userRole = userRoleMapper.selectOne(new QueryWrapper<UserRole>().eq("user_id", user.getId()));
        if (userRole != null) {
            user.setRoleId(userRole.getRoleId());
            Role role = roleMapper.selectById(userRole.getRoleId());
            if (role != null) {
                user.setRoleCode(role.getRoleCode());
            }
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
