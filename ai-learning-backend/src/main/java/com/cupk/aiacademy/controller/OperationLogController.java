package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
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
    public Result pages(@RequestParam(required = false) Integer userId,
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
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        OperationLog log = logMapper.selectById(id);
        return log == null ? Result.error("404", "操作日志不存在") : Result.success(log);
    }

    @PostMapping
    public Result add(@RequestBody(required = false) OperationLog log) {
        Result checkResult = checkLog(log);
        if (checkResult != null) {
            return checkResult;
        }
        log.setCreateTime(LocalDateTime.now());
        int row = logMapper.insert(log);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody OperationLog log) {
        if (logMapper.selectById(id) == null) {
            return Result.error("404", "操作日志不存在");
        }
        Result checkResult = checkLog(log);
        if (checkResult != null) {
            return checkResult;
        }
        log.setId(id);
        int row = logMapper.updateById(log);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (logMapper.selectById(id) == null) {
            return Result.error("404", "操作日志不存在");
        }
        int row = logMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkLog(OperationLog log) {
        if (log == null) {
            return Result.error("400", "操作日志信息不能为空");
        }
        if (log.getUserId() != null && userMapper.selectById(log.getUserId()) == null) {
            return Result.error("404", "用户不存在");
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
