package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.LearningProgress;
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
    public ApiResponse<Page<LearningProgress>> pages(@RequestParam(required = false) Integer classId,
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
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningProgress> selectById(@PathVariable Integer id) {
        LearningProgress progress = progressMapper.selectById(id);
        if (progress == null) {
            throw new BusinessException("学习进度不存在");
        }
        return ApiResponse.success(progress);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody LearningProgress progress) {
        checkProgress(progress);
        LocalDateTime now = LocalDateTime.now();
        progress.setCreateTime(now);
        progress.setUpdateTime(now);
        int row = progressMapper.insert(progress);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody LearningProgress progress) {
        if (progressMapper.selectById(id) == null) {
            throw new BusinessException("学习进度不存在");
        }
        checkProgress(progress);
        progress.setId(id);
        progress.setUpdateTime(LocalDateTime.now());
        int row = progressMapper.updateById(progress);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (progressMapper.selectById(id) == null) {
            throw new BusinessException("学习进度不存在");
        }
        int row = progressMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkProgress(LearningProgress progress) {
        if (progress == null) {
            throw new BusinessException("学习进度信息不能为空");
        }
        if (progress.getClassId() == null || classMapper.selectById(progress.getClassId()) == null) {
            throw new BusinessException("教学班不存在");
        }
        if (progress.getStudentId() == null || userMapper.selectById(progress.getStudentId()) == null) {
            throw new BusinessException("学生用户不存在");
        }
        if (progress.getLessonId() == null || lessonMapper.selectById(progress.getLessonId()) == null) {
            throw new BusinessException("课时不存在");
        }
        if (progress.getProgress() != null && (progress.getProgress() < 0 || progress.getProgress() > 100)) {
            throw new BusinessException("学习进度应在0到100之间");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
