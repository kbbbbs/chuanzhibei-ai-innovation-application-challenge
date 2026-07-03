package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.OperationLog;
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
@RequestMapping("/operation-logs")
public class OperationLogController {
    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public ApiResponse<Page<OperationLog>> pages(@RequestParam(required = false) Integer userId,
                        @RequestParam(defaultValue = "") String operation,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<OperationLog> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<OperationLog> queryWrapper = new QueryWrapper<>();
        if (userId != null && userId > 0) {
            queryWrapper.eq("user_id", userId);
        }
        if (operation != null && !operation.trim().isEmpty()) {
            queryWrapper.like("operation", operation.trim());
        }
        queryWrapper.orderByDesc("id");
        logMapper.selectPage(page, queryWrapper);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<OperationLog> selectById(@PathVariable Integer id) {
        OperationLog log = logMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("操作日志不存在");
        }
        return ApiResponse.success(log);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody OperationLog log) {
        checkLog(log);
        log.setCreateTime(LocalDateTime.now());
        int row = logMapper.insert(log);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody OperationLog log) {
        if (logMapper.selectById(id) == null) {
            throw new BusinessException("操作日志不存在");
        }
        checkLog(log);
        log.setId(id);
        int row = logMapper.updateById(log);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (logMapper.selectById(id) == null) {
            throw new BusinessException("操作日志不存在");
        }
        int row = logMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkLog(OperationLog log) {
        if (log == null) {
            throw new BusinessException("操作日志信息不能为空");
        }
        if (log.getUserId() != null && userMapper.selectById(log.getUserId()) == null) {
            throw new BusinessException("用户不存在");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
