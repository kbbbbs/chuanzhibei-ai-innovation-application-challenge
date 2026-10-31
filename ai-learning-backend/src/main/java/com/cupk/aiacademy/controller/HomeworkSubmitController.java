package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.LegacyHomeworkWritePolicy;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/homework-submits")
public class HomeworkSubmitController {
    @Autowired
    private HomeworkSubmitMapper submitMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer homeworkId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<HomeworkSubmit> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isStudent(user)) {
            queryWrapper.eq("student_id", user.getUserId());
            if (homeworkId != null && homeworkId > 0) {
                Homework homework = homeworkMapper.selectById(homeworkId);
                if (homework == null || !permissionService.isStudentInClass(homework.getClassId(), user.getUserId())) {
                    return Result.success(page);
                }
                queryWrapper.eq("homework_id", homeworkId);
            }
        } else if (authUtils.isTeacher(user)) {
            if (homeworkId != null && homeworkId > 0) {
                Homework homework = homeworkMapper.selectById(homeworkId);
                if (homework == null) {
                    return Result.error("404", "作业不存在");
                }
                permissionService.requireHomeworkTeacher(homework);
                queryWrapper.eq("homework_id", homeworkId);
            } else {
                java.util.List<Integer> classIds = permissionService.teacherClassIds(user.getUserId());
                if (classIds.isEmpty()) {
                    return Result.success(page);
                }
                QueryWrapper<Homework> homeworkQuery = new QueryWrapper<>();
                homeworkQuery.in("class_id", classIds);
                java.util.List<Integer> homeworkIds = homeworkMapper.selectList(homeworkQuery).stream()
                        .map(Homework::getId)
                        .filter(id -> id != null && id > 0)
                        .toList();
                if (homeworkIds.isEmpty()) {
                    return Result.success(page);
                }
                queryWrapper.in("homework_id", homeworkIds);
            }
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
        } else {
            if (homeworkId != null && homeworkId > 0) {
                queryWrapper.eq("homework_id", homeworkId);
            }
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
        }
        queryWrapper.orderByDesc("id");
        submitMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        HomeworkSubmit submit = submitMapper.selectById(id);
        if (submit == null) {
            return Result.error("404", "作业提交不存在");
        }
        checkSubmitReadPermission(submit);
        return Result.success(submit);
    }

    @PostMapping
    public Result add() {
        return LegacyHomeworkWritePolicy.rejectedResult();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id) {
        return LegacyHomeworkWritePolicy.rejectedResult();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        return LegacyHomeworkWritePolicy.rejectedResult();
    }

    private void checkSubmitReadPermission(HomeworkSubmit submit) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isStudent(user)) {
            if (!user.getUserId().equals(submit.getStudentId())) {
                throw new com.cupk.aiacademy.common.ForbiddenException("只能查看自己的作业提交");
            }
            return;
        }
        Homework homework = homeworkMapper.selectById(submit.getHomeworkId());
        if (homework == null) {
            throw new com.cupk.aiacademy.common.BusinessException("作业不存在");
        }
        permissionService.requireHomeworkTeacher(homework);
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
