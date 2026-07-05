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
import com.cupk.aiacademy.pojo.TeachingClass;
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

    @Autowired(required = false)
    private AuthUtils authUtils;

    @Autowired
    private PermissionService permissionService;

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
        if (homeworkMapper.selectById(homeworkId) == null) {
            return Result.error("404", "作业不存在");
        }
        Homework homework = homeworkMapper.selectById(homeworkId);
        permissionService.requireHomeworkTeacher(homework);
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("homework_id", homeworkId).orderByDesc("id");
        return Result.success(submitMapper.selectList(queryWrapper));
    }

    @PostMapping("/homeworks")
    public Result add(@Valid @RequestBody HomeworkDto request) {
        permissionService.requireTeacherOrAdmin();
        Homework homework = request.toEntity();
        applyHomeworkTeacher(homework);
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

        permissionService.requireHomeworkTeacher(homework);

        homework = request.toEntity();
        applyHomeworkTeacher(homework);
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

        permissionService.requireHomeworkTeacher(homework);

        int row = homeworkMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkHomework(Homework homework) {
        if (homework == null) {
            return Result.error("400", "作业信息不能为空");
        }
        TeachingClass teachingClass = homework.getClassId() == null ? null : classMapper.selectById(homework.getClassId());
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        if (homework.getTeacherId() == null || homework.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        try {
            permissionService.requireUserRole(homework.getTeacherId(), "teacher", "教师用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        if (!homework.getTeacherId().equals(teachingClass.getTeacherId())) {
            return Result.error("400", "作业教师必须与教学班教师一致");
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

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return authUtils != null && authUtils.isAdmin(user);
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return authUtils != null && authUtils.isTeacher(user);
    }

    private void applyHomeworkTeacher(Homework homework) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (isTeacher(user)) {
            homework.setTeacherId(user.getUserId());
        } else if (homework.getClassId() != null && (homework.getTeacherId() == null || homework.getTeacherId() <= 0)) {
            TeachingClass teachingClass = classMapper.selectById(homework.getClassId());
            if (teachingClass != null) {
                homework.setTeacherId(teachingClass.getTeacherId());
            }
        }
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
