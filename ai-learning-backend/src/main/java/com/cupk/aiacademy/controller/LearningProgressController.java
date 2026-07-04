package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.LearningProgressDto;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.LearningProgress;
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
@RequestMapping("/learning-progress")
public class LearningProgressController {
    @Autowired
    private LearningProgressMapper progressMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LessonMapper lessonMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(required = false) Integer lessonId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<LearningProgress> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<LearningProgress> queryWrapper = new QueryWrapper<>();
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        if (studentId != null && studentId > 0) {
            queryWrapper.eq("student_id", studentId);
        }
        if (lessonId != null && lessonId > 0) {
            queryWrapper.eq("lesson_id", lessonId);
        }
        queryWrapper.orderByDesc("id");
        progressMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        LearningProgress progress = progressMapper.selectById(id);
        return progress == null ? Result.error("404", "学习进度不存在") : Result.success(progress);
    }

    @PostMapping
    public Result add(@Valid @RequestBody LearningProgressDto request) {
        LearningProgress progress = request.toEntity();
        Result checkResult = checkProgress(progress);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        progress.setCreateTime(now);
        progress.setUpdateTime(now);
        int row = progressMapper.insert(progress);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody LearningProgressDto request) {
        LearningProgress progress = request.toEntity();
        if (progressMapper.selectById(id) == null) {
            return Result.error("404", "学习进度不存在");
        }
        Result checkResult = checkProgress(progress);
        if (checkResult != null) {
            return checkResult;
        }
        progress.setId(id);
        progress.setUpdateTime(LocalDateTime.now());
        int row = progressMapper.updateById(progress);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (progressMapper.selectById(id) == null) {
            return Result.error("404", "学习进度不存在");
        }
        int row = progressMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkProgress(LearningProgress progress) {
        if (progress == null) {
            return Result.error("400", "学习进度信息不能为空");
        }
        if (progress.getClassId() == null || classMapper.selectById(progress.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
        }
        if (progress.getStudentId() == null || userMapper.selectById(progress.getStudentId()) == null) {
            return Result.error("404", "学生用户不存在");
        }
        if (progress.getLessonId() == null || lessonMapper.selectById(progress.getLessonId()) == null) {
            return Result.error("404", "课时不存在");
        }
        if (progress.getProgress() != null && (progress.getProgress() < 0 || progress.getProgress() > 100)) {
            return Result.error("400", "学习进度应在0到100之间");
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
