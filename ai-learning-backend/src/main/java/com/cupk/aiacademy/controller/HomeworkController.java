package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.HomeworkDto;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.service.HomeworkService;
import com.cupk.aiacademy.service.PermissionService;
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

@RestController
public class HomeworkController {
    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private HomeworkSubmitMapper submitMapper;

    @Autowired(required = false)
    private AuthUtils authUtils;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private HomeworkService homeworkService;

    @GetMapping("/homeworks")
    public Result pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Homework> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Homework> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (isTeacher(user)) {
            if (classId != null && classId > 0) {
                permissionService.requireClassTeacher(classId);
                queryWrapper.eq("class_id", classId);
            } else {
                java.util.List<Integer> classIds = permissionService.teacherClassIds(user.getUserId());
                if (classIds.isEmpty()) {
                    return Result.success(page);
                }
                queryWrapper.in("class_id", classIds);
            }
            queryWrapper.eq("teacher_id", user.getUserId());
        } else if (authUtils.isStudent(user)) {
            java.util.List<Integer> classIds = permissionService.studentClassIds(user.getUserId());
            if (classIds.isEmpty()) {
                return Result.success(page);
            }
            queryWrapper.in("class_id", classIds);
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
        } else {
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
            if (teacherId != null && teacherId > 0) {
                queryWrapper.eq("teacher_id", teacherId);
            }
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
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }
        checkHomeworkReadPermission(homework);
        return Result.success(homework);
    }

    @GetMapping("/homeworks/{homeworkId}/submits")
    public Result listSubmits(@PathVariable Integer homeworkId) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }
        permissionService.requireHomeworkTeacher(homework);
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("homework_id", homeworkId).orderByDesc("id");
        return Result.success(submitMapper.selectList(queryWrapper));
    }

    @PostMapping("/homeworks")
    public Result add(@Valid @RequestBody HomeworkDto request) {
        return homeworkService.create(request);
    }

    @PutMapping("/homeworks/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody HomeworkDto request) {
        return homeworkService.update(id, request);
    }

    @DeleteMapping("/homeworks/{id}")
    public Result delete(@PathVariable Integer id) {
        Homework homework = homeworkMapper.selectById(id);
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }

        permissionService.requireHomeworkTeacher(homework);

        int row = homeworkMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return authUtils != null && authUtils.isAdmin(user);
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return authUtils != null && authUtils.isTeacher(user);
    }

    private void checkHomeworkReadPermission(Homework homework) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (isAdmin(user)) {
            return;
        }
        if (isTeacher(user)) {
            permissionService.requireHomeworkTeacher(homework);
            return;
        }
        if (!permissionService.isStudentInClass(homework.getClassId(), user.getUserId())) {
            throw new com.cupk.aiacademy.common.ForbiddenException("无权查看该作业");
        }
    }
}
