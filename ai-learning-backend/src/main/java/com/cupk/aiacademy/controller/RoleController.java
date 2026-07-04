package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.RoleDto;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.UserRole;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/roles")
public class RoleController {
    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @GetMapping
    public Result pages(@RequestParam(defaultValue = "") String roleName,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Role> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
        if (roleName != null && !roleName.trim().isEmpty()) {
            queryWrapper.like("role_name", roleName.trim());
        }
        queryWrapper.orderByAsc("id");
        roleMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        Role role = roleMapper.selectById(id);
        return role == null ? Result.error("404", "角色不存在") : Result.success(role);
    }

    @PostMapping
    public Result add(@Valid @RequestBody RoleDto request) {
        Role role = request.toEntity();
        Result checkResult = checkRole(role, null);
        if (checkResult != null) {
            return checkResult;
        }
        role.setRoleName(role.getRoleName().trim());
        role.setRoleCode(role.getRoleCode().trim());
        role.setCreateTime(LocalDateTime.now());
        int row = roleMapper.insert(role);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody RoleDto request) {
        Role role = request.toEntity();
        if (roleMapper.selectById(id) == null) {
            return Result.error("404", "角色不存在");
        }
        Result checkResult = checkRole(role, id);
        if (checkResult != null) {
            return checkResult;
        }
        role.setId(id);
        role.setRoleName(role.getRoleName().trim());
        role.setRoleCode(role.getRoleCode().trim());
        int row = roleMapper.updateById(role);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (roleMapper.selectById(id) == null) {
            return Result.error("404", "角色不存在");
        }
        Long userCount = userRoleMapper.selectCount(new QueryWrapper<UserRole>().eq("role_id", id));
        if (userCount > 0) {
            return Result.error("409", "角色已被用户使用，不能删除");
        }
        int row = roleMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkRole(Role role, Integer excludeId) {
        if (role == null) {
            return Result.error("400", "角色信息不能为空");
        }
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return Result.error("400", "角色名称不能为空");
        }
        if (role.getRoleCode() == null || role.getRoleCode().trim().isEmpty()) {
            return Result.error("400", "角色编码不能为空");
        }
        QueryWrapper<Role> duplicateQuery = new QueryWrapper<>();
        duplicateQuery.eq("role_code", role.getRoleCode().trim());
        if (excludeId != null) {
            duplicateQuery.ne("id", excludeId);
        }
        if (roleMapper.selectCount(duplicateQuery) > 0) {
            return Result.error("409", "角色编码已存在");
        }
        return null;
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
