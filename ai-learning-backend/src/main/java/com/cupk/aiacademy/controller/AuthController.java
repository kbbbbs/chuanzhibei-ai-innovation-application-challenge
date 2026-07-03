package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.JwtUtils;
import com.cupk.aiacademy.common.MD5Utils;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody User form) {
        if (isBlank(form.getUsername()) || isBlank(form.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }

        User oldUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", form.getUsername()));
        if (oldUser != null) {
            throw new BusinessException("用户名已存在");
        }

        Role studentRole = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "student"));
        if (studentRole == null) {
            throw new BusinessException("学生角色未初始化");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(MD5Utils.md5(form.getPassword()));
        user.setNickname(isBlank(form.getNickname()) ? form.getUsername() : form.getNickname());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setAvatar(form.getAvatar());
        user.setStatus(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(studentRole.getId());
        userRoleMapper.insert(userRole);

        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody User form) {
        if (isBlank(form.getUsername()) || isBlank(form.getPassword())) {
            throw new BusinessException("请输入用户名和密码");
        }

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", form.getUsername()));
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!MD5Utils.md5(form.getPassword()).equals(user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        Role role = getUserRole(user.getId());
        String roleCode = role == null ? "student" : role.getRoleCode();
        String token = jwtUtils.createToken(user.getId(), user.getUsername(), roleCode);

        Map<String, Object> data = buildUserInfo(user, roleCode);
        data.put("token", token);
        return ApiResponse.success(data);
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("请先登录");
        }

        try {
            Claims claims = jwtUtils.parseToken(authorization.substring(7));
            Integer userId = ((Number) claims.get("userId")).intValue();
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            Role role = getUserRole(user.getId());
            return ApiResponse.success(buildUserInfo(user, role == null ? "student" : role.getRoleCode()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("登录状态已失效");
        }
    }

    private Role getUserRole(Integer userId) {
        UserRole userRole = userRoleMapper.selectOne(new QueryWrapper<UserRole>().eq("user_id", userId));
        if (userRole == null) {
            return null;
        }
        return roleMapper.selectById(userRole.getRoleId());
    }

    private Map<String, Object> buildUserInfo(User user, String roleCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("avatar", user.getAvatar());
        data.put("roleCode", roleCode);
        return data;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
