package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.HomeworkSubmitDto;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
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
@RequestMapping("/homework-submits")
public class HomeworkSubmitController {
    @Autowired
    private HomeworkSubmitMapper submitMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer homeworkId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<HomeworkSubmit> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        if (homeworkId != null && homeworkId > 0) {
            queryWrapper.eq("homework_id", homeworkId);
        }
        if (studentId != null && studentId > 0) {
            queryWrapper.eq("student_id", studentId);
        }
        queryWrapper.orderByDesc("id");
        submitMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        HomeworkSubmit submit = submitMapper.selectById(id);
        return submit == null ? Result.error("404", "作业提交不存在") : Result.success(submit);
    }

    @PostMapping
    public Result add(@Valid @RequestBody HomeworkSubmitDto request) {
        HomeworkSubmit submit = request.toEntity();
        Result checkResult = checkSubmit(submit);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        if (submit.getSubmitTime() == null) {
            submit.setSubmitTime(now);
        }
        submit.setCreateTime(now);
        submit.setUpdateTime(now);
        int row = submitMapper.insert(submit);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody HomeworkSubmitDto request) {
        HomeworkSubmit submit = request.toEntity();
        if (submitMapper.selectById(id) == null) {
            return Result.error("404", "作业提交不存在");
        }
        Result checkResult = checkSubmit(submit);
        if (checkResult != null) {
            return checkResult;
        }
        submit.setId(id);
        submit.setUpdateTime(LocalDateTime.now());
        int row = submitMapper.updateById(submit);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (submitMapper.selectById(id) == null) {
            return Result.error("404", "作业提交不存在");
        }
        int row = submitMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkSubmit(HomeworkSubmit submit) {
        if (submit == null) {
            return Result.error("400", "作业提交信息不能为空");
        }
        if (submit.getHomeworkId() == null || homeworkMapper.selectById(submit.getHomeworkId()) == null) {
            return Result.error("404", "作业不存在");
        }
        if (submit.getStudentId() == null || userMapper.selectById(submit.getStudentId()) == null) {
            return Result.error("404", "学生用户不存在");
        }
        if (submit.getGraderId() != null && userMapper.selectById(submit.getGraderId()) == null) {
            return Result.error("404", "批改教师不存在");
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
