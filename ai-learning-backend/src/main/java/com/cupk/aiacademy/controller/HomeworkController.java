package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.HomeworkDto;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import jakarta.validation.Valid;
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

    @Autowired
    private AuthUtils authUtils;

    @GetMapping("/homeworks")
    public Result pages(@RequestParam(required = false) Integer classId,
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
        return Result.success(page);
    }

    @GetMapping("/homeworks/{id}")
    public Result selectById(@PathVariable Integer id) {
        Homework homework = homeworkMapper.selectById(id);
        return homework == null ? Result.error("404", "作业不存在") : Result.success(homework);
    }

    @GetMapping("/homeworks/{homeworkId}/submits")
    public Result listSubmits(@PathVariable Integer homeworkId) {
        if (homeworkMapper.selectById(homeworkId) == null) {
            return Result.error("404", "作业不存在");
        }
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("homework_id", homeworkId).orderByDesc("id");
        return Result.success(submitMapper.selectList(queryWrapper));
    }

    @PostMapping("/homeworks")
    public Result add(@Valid @RequestBody HomeworkDto request) {
        Homework homework = request.toEntity();
        Result checkResult = checkHomework(homework);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        homework.setTitle(homework.getTitle().trim());
        homework.setCreateTime(now);
        homework.setUpdateTime(now);
        int row = homeworkMapper.insert(homework);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/homeworks/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody HomeworkDto request) {
        Homework homework = homeworkMapper.selectById(id);
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }

        // Permission check: teacher can only modify their own homework
        AuthUtils.LoginUser user = UserContext.get();
        if (user != null && !authUtils.isAdmin(user) && homework.getTeacherId() != null
                && !homework.getTeacherId().equals(user.getUserId())) {
            return Result.error("403", "无权限修改此作业");
        }

        homework = request.toEntity();
        Result checkResult = checkHomework(homework);
        if (checkResult != null) {
            return checkResult;
        }
        homework.setId(id);
        homework.setTitle(homework.getTitle().trim());
        homework.setUpdateTime(LocalDateTime.now());
        int row = homeworkMapper.updateById(homework);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/homeworks/{id}")
    public Result delete(@PathVariable Integer id) {
        Homework homework = homeworkMapper.selectById(id);
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }

        // Permission check: teacher can only delete their own homework
        AuthUtils.LoginUser user = UserContext.get();
        if (user != null && !authUtils.isAdmin(user) && homework.getTeacherId() != null
                && !homework.getTeacherId().equals(user.getUserId())) {
            return Result.error("403", "无权限删除此作业");
        }

        int row = homeworkMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkHomework(Homework homework) {
        if (homework == null) {
            return Result.error("400", "作业信息不能为空");
        }
        if (homework.getClassId() == null || classMapper.selectById(homework.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
        }
        if (homework.getTeacherId() == null || userMapper.selectById(homework.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (homework.getTitle() == null || homework.getTitle().trim().isEmpty()) {
            return Result.error("400", "作业标题不能为空");
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
