package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.pojo.Role;
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

    @GetMapping
    public ApiResponse<Page<Role>> pages(@RequestParam(defaultValue = "") String roleName,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Role> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
        if (roleName != null && !roleName.trim().isEmpty()) {
            queryWrapper.like("role_name", roleName.trim());
        }
        queryWrapper.orderByAsc("id");
        roleMapper.selectPage(page, queryWrapper);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<Role> selectById(@PathVariable Integer id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return ApiResponse.success(role);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody Role role) {
        checkRole(role);
        role.setRoleName(role.getRoleName().trim());
        role.setRoleCode(role.getRoleCode().trim());
        role.setCreateTime(LocalDateTime.now());
        int row = roleMapper.insert(role);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody Role role) {
        if (roleMapper.selectById(id) == null) {
            throw new BusinessException("角色不存在");
        }
        checkRole(role);
        role.setId(id);
        role.setRoleName(role.getRoleName().trim());
        role.setRoleCode(role.getRoleCode().trim());
        int row = roleMapper.updateById(role);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (roleMapper.selectById(id) == null) {
            throw new BusinessException("角色不存在");
        }
        int row = roleMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkRole(Role role) {
        if (role == null) {
            throw new BusinessException("角色信息不能为空");
        }
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            throw new BusinessException("角色名称不能为空");
        }
        if (role.getRoleCode() == null || role.getRoleCode().trim().isEmpty()) {
            throw new BusinessException("角色编码不能为空");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
