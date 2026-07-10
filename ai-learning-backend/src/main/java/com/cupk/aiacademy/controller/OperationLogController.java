package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.OperationLogDto;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.OperationLog;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/operation-logs")
public class OperationLogController {
    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer userId,
                        @RequestParam(defaultValue = "") String operation,
                        @RequestParam(defaultValue = "") String method,
                        @RequestParam(defaultValue = "") String url,
                        @RequestParam(required = false) Integer status,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<OperationLog> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<OperationLog> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser currentUser = permissionService.currentUser();
        List<Integer> visibleStudentIds = visibleStudentIds(currentUser);

        if (visibleStudentIds != null) {
            if (visibleStudentIds.isEmpty()) {
                return Result.success(page);
            }
            if (userId != null && userId > 0 && !visibleStudentIds.contains(userId)) {
                return Result.success(page);
            }
            queryWrapper.in("user_id", visibleStudentIds);
        }
        if (userId != null && userId > 0) {
            queryWrapper.eq("user_id", userId);
        }
        if (operation != null && !operation.trim().isEmpty()) {
            queryWrapper.like("operation", operation.trim());
        }
        if (method != null && !method.trim().isEmpty()) {
            queryWrapper.eq("method", method.trim().toUpperCase());
        }
        if (url != null && !url.trim().isEmpty()) {
            queryWrapper.like("url", url.trim());
        }
        if (status != null && status > 0) {
            queryWrapper.like("params", "\"status\":" + status);
        }
        if (startTime != null) {
            queryWrapper.ge("create_time", startTime);
        }
        if (endTime != null) {
            queryWrapper.le("create_time", endTime);
        }
        queryWrapper.orderByDesc("id");
        logMapper.selectPage(page, queryWrapper);
        enrichUsers(page.getRecords());
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        OperationLog log = logMapper.selectById(id);
        if (log == null) {
            return Result.error("404", "操作日志不存在");
        }
        if (!canRead(log)) {
            throw new ForbiddenException("无权查看该操作日志");
        }
        enrichUser(log);
        return Result.success(log);
    }

    @PostMapping
    public Result add(@Valid @RequestBody(required = false) OperationLogDto request) {
        OperationLog log = request == null ? null : request.toEntity();
        Result checkResult = checkLog(log);
        if (checkResult != null) {
            return checkResult;
        }
        log.setCreateTime(LocalDateTime.now());
        int row = logMapper.insert(log);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody OperationLogDto request) {
        OperationLog log = request.toEntity();
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

    private List<Integer> visibleStudentIds(AuthUtils.LoginUser currentUser) {
        if (permissionService.isAdmin(currentUser)) {
            return null;
        }
        if (permissionService.isTeacher(currentUser)) {
            return permissionService.teacherStudentIds(currentUser.getUserId());
        }
        throw new ForbiddenException("仅管理员或教师可查看操作日志");
    }

    private boolean canRead(OperationLog log) {
        AuthUtils.LoginUser currentUser = permissionService.currentUser();
        List<Integer> visibleStudentIds = visibleStudentIds(currentUser);
        return visibleStudentIds == null || visibleStudentIds.contains(log.getUserId());
    }

    private void enrichUsers(List<OperationLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        List<Integer> userIds = logs.stream()
                .map(OperationLog::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Integer, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        logs.forEach(log -> fillUser(log, userMap.get(log.getUserId())));
    }

    private void enrichUser(OperationLog log) {
        if (log == null || log.getUserId() == null) {
            return;
        }
        fillUser(log, userMapper.selectById(log.getUserId()));
    }

    private void fillUser(OperationLog log, User user) {
        if (log == null || user == null) {
            return;
        }
        log.setUsername(user.getUsername());
        log.setNickname(user.getNickname());
    }
}
