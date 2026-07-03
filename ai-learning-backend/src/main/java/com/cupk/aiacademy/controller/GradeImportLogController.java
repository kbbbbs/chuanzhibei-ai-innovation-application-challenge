package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.GradeImportLogMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.GradeImportLog;
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
@RequestMapping("/grade-import-logs")
public class GradeImportLogController {
    @Autowired
    private GradeImportLogMapper logMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer teacherId,
                        @RequestParam(required = false) Integer classId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<GradeImportLog> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<GradeImportLog> queryWrapper = new QueryWrapper<>();
        if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        queryWrapper.orderByDesc("id");
        logMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        GradeImportLog log = logMapper.selectById(id);
        return log == null ? Result.error("404", "成绩导入日志不存在") : Result.success(log);
    }

    @PostMapping
    public Result add(@RequestBody GradeImportLog log) {
        Result checkResult = checkLog(log);
        if (checkResult != null) {
            return checkResult;
        }
        log.setCreateTime(LocalDateTime.now());
        int row = logMapper.insert(log);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody GradeImportLog log) {
        if (logMapper.selectById(id) == null) {
            return Result.error("404", "成绩导入日志不存在");
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
            return Result.error("404", "成绩导入日志不存在");
        }
        int row = logMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkLog(GradeImportLog log) {
        if (log == null) {
            return Result.error("400", "成绩导入日志不能为空");
        }
        if (log.getTeacherId() == null || log.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        if (log.getClassId() == null || log.getClassId() <= 0) {
            return Result.error("400", "教学班不能为空");
        }
        if (userMapper.selectById(log.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (classMapper.selectById(log.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
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
