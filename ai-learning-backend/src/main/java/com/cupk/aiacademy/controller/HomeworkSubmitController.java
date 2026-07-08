package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.HomeworkSubmitDto;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.UserMapper;
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
    public Result add(@RequestBody HomeworkSubmitDto request) {
        permissionService.requireStudent();
        HomeworkSubmit submit = request == null ? null : request.toEntity();
        if (submit != null) {
            prepareStudentSubmit(submit);
        }
        Result checkResult = checkSubmit(submit);
        if (checkResult != null) {
            return checkResult;
        }
        stampSubmitForCreate(submit);
        int row = submitMapper.insert(submit);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody HomeworkSubmitDto request) {
        HomeworkSubmit submit = request == null ? null : request.toEntity();
        HomeworkSubmit old = submitMapper.selectById(id);
        if (old == null) {
            return Result.error("404", "作业提交不存在");
        }
        if (submit == null) {
            return Result.error("400", "作业提交信息不能为空");
        }
        Result prepareResult = prepareSubmitForUpdate(submit, old);
        if (prepareResult != null) {
            return prepareResult;
        }
        Result checkResult = checkSubmit(submit);
        if (checkResult != null) {
            return checkResult;
        }
        stampSubmitForUpdate(submit, id);
        int row = submitMapper.updateById(submit);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        HomeworkSubmit submit = submitMapper.selectById(id);
        if (submit == null) {
            return Result.error("404", "作业提交不存在");
        }
        Homework homework = homeworkMapper.selectById(submit.getHomeworkId());
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }
        permissionService.requireHomeworkTeacher(homework);
        int row = submitMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private void prepareStudentSubmit(HomeworkSubmit submit) {
        submit.setStudentId(permissionService.currentUser().getUserId());
        submit.setScore(null);
        submit.setFeedback(null);
        submit.setGradeStatus(0);
        submit.setGraderId(null);
        submit.setGradeTime(null);
    }

    private Result prepareSubmitForUpdate(HomeworkSubmit submit, HomeworkSubmit old) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isStudent(user)) {
            if (!user.getUserId().equals(old.getStudentId())) {
                return Result.error("403", "只能修改自己的作业提交");
            }
            preserveStudentSubmitFields(submit, old);
            return null;
        }

        Homework homework = homeworkMapper.selectById(old.getHomeworkId());
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }
        permissionService.requireHomeworkTeacher(homework);
        prepareTeacherGradeFields(submit, old, user);
        return null;
    }

    private void preserveStudentSubmitFields(HomeworkSubmit submit, HomeworkSubmit old) {
        submit.setHomeworkId(old.getHomeworkId());
        submit.setStudentId(old.getStudentId());
        submit.setScore(old.getScore());
        submit.setFeedback(old.getFeedback());
        submit.setGradeStatus(old.getGradeStatus());
        submit.setGraderId(old.getGraderId());
        submit.setGradeTime(old.getGradeTime());
    }

    private void prepareTeacherGradeFields(HomeworkSubmit submit, HomeworkSubmit old, AuthUtils.LoginUser user) {
        submit.setHomeworkId(old.getHomeworkId());
        submit.setStudentId(old.getStudentId());
        submit.setGraderId(user.getUserId());
        if (submit.getGradeTime() == null) {
            submit.setGradeTime(LocalDateTime.now());
        }
    }

    private void stampSubmitForCreate(HomeworkSubmit submit) {
        LocalDateTime now = LocalDateTime.now();
        if (submit.getSubmitTime() == null) {
            submit.setSubmitTime(now);
        }
        submit.setCreateTime(now);
        submit.setUpdateTime(now);
    }

    private void stampSubmitForUpdate(HomeworkSubmit submit, Integer id) {
        submit.setId(id);
        submit.setUpdateTime(LocalDateTime.now());
    }

    private Result checkSubmit(HomeworkSubmit submit) {
        if (submit == null) {
            return Result.error("400", "作业提交信息不能为空");
        }
        if (submit.getHomeworkId() == null || submit.getHomeworkId() <= 0) {
            return Result.error("400", "作业不能为空");
        }
        if (submit.getStudentId() == null || submit.getStudentId() <= 0) {
            return Result.error("400", "学生用户不能为空");
        }
        if (isBlank(submit.getSubmitContent()) && isBlank(submit.getAttachmentUrl())) {
            return Result.error("400", "请填写作业内容或上传附件");
        }
        Homework homework = homeworkMapper.selectById(submit.getHomeworkId());
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }
        try {
            permissionService.requireUserRole(submit.getStudentId(), "student", "学生用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        if (!permissionService.isStudentInClass(homework.getClassId(), submit.getStudentId())) {
            return Result.error("400", "学生未加入作业所属教学班");
        }
        if (submit.getGraderId() != null && userMapper.selectById(submit.getGraderId()) == null) {
            return Result.error("404", "批改教师不存在");
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
