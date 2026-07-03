package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class HomeworkController {
    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private HomeworkSubmitMapper submitMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/homeworks")
    public ApiResponse<Page<Homework>> pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Homework> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Homework> queryWrapper = new QueryWrapper<>();
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        queryWrapper.orderByDesc("id");
        homeworkMapper.selectPage(page, queryWrapper);
        return ApiResponse.success(page);
    }

    @GetMapping("/homeworks/{id}")
    public ApiResponse<Homework> selectById(@PathVariable Integer id) {
        Homework homework = homeworkMapper.selectById(id);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }
        return ApiResponse.success(homework);
    }

    @GetMapping("/homeworks/{homeworkId}/submits")
    public ApiResponse<Object> listSubmits(@PathVariable Integer homeworkId) {
        if (homeworkMapper.selectById(homeworkId) == null) {
            throw new BusinessException("作业不存在");
        }
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("homework_id", homeworkId).orderByDesc("id");
        return ApiResponse.success(submitMapper.selectList(queryWrapper));
    }

    @PostMapping("/homeworks")
    public ApiResponse<Void> add(@RequestBody Homework homework) {
        checkHomework(homework);
        LocalDateTime now = LocalDateTime.now();
        homework.setTitle(homework.getTitle().trim());
        homework.setCreateTime(now);
        homework.setUpdateTime(now);
        int row = homeworkMapper.insert(homework);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/homeworks/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody Homework homework) {
        if (homeworkMapper.selectById(id) == null) {
            throw new BusinessException("作业不存在");
        }
        checkHomework(homework);
        homework.setId(id);
        homework.setTitle(homework.getTitle().trim());
        homework.setUpdateTime(LocalDateTime.now());
        int row = homeworkMapper.updateById(homework);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/homeworks/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (homeworkMapper.selectById(id) == null) {
            throw new BusinessException("作业不存在");
        }
        int row = homeworkMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkHomework(Homework homework) {
        if (homework == null) {
            throw new BusinessException("作业信息不能为空");
        }
        if (homework.getClassId() == null || classMapper.selectById(homework.getClassId()) == null) {
            throw new BusinessException("教学班不存在");
        }
        if (homework.getTeacherId() == null || userMapper.selectById(homework.getTeacherId()) == null) {
            throw new BusinessException("教师用户不存在");
        }
        if (homework.getTitle() == null || homework.getTitle().trim().isEmpty()) {
            throw new BusinessException("作业标题不能为空");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
