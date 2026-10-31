package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.ClassStudentDto;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/class-students")
public class ClassStudentController {
    @Autowired
    private ClassStudentMapper classStudentMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ClassStudent> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ClassStudent> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
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
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
        } else if (authUtils.isStudent(user)) {
            queryWrapper.eq("student_id", user.getUserId());
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
        } else {
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
        }
        queryWrapper.orderByDesc("id");
        classStudentMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        ClassStudent classStudent = classStudentMapper.selectById(id);
        if (classStudent == null) {
            return Result.error("404", "学生名单记录不存在");
        }
        checkReadPermission(classStudent);
        return Result.success(classStudent);
    }

    @GetMapping("/class/{classId}")
    public Result listByClass(@PathVariable Integer classId) {
        if (classMapper.selectById(classId) == null) {
            return Result.error("404", "教学班不存在");
        }
        permissionService.requireClassTeacher(classId);
        QueryWrapper<ClassStudent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("class_id", classId).orderByDesc("id");
        return Result.success(classStudentMapper.selectList(queryWrapper));
    }

    @GetMapping("/student/{studentId}")
    public Result listByStudent(@PathVariable Integer studentId) {
        if (userMapper.selectById(studentId) == null) {
            return Result.error("404", "学生用户不存在");
        }
        AuthUtils.LoginUser user = permissionService.currentUser();
        QueryWrapper<ClassStudent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId).orderByDesc("id");
        if (authUtils.isStudent(user)) {
            if (!user.getUserId().equals(studentId)) {
                return Result.error("403", "只能查看自己的班级");
            }
            queryWrapper.eq("status", 1);
        } else if (authUtils.isTeacher(user)) {
            java.util.List<Integer> classIds = permissionService.teacherClassIds(user.getUserId());
            if (classIds.isEmpty()) {
                return Result.success(java.util.List.of());
            }
            queryWrapper.in("class_id", classIds);
        }
        return Result.success(classStudentMapper.selectList(queryWrapper));
    }

    @PostMapping
    public Result add(@RequestBody ClassStudentDto request) {
        ClassStudent classStudent = request == null ? null : request.toEntity();
        Result checkResult = checkClassStudent(classStudent, null);
        if (checkResult != null) {
            return checkResult;
        }
        permissionService.requireClassTeacher(classStudent.getClassId());
        classStudent.setJoinTime(LocalDateTime.now());
        int row = classStudentMapper.insert(classStudent);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ClassStudentDto request) {
        ClassStudent classStudent = request == null ? null : request.toEntity();
        ClassStudent old = classStudentMapper.selectById(id);
        if (old == null) {
            return Result.error("404", "学生名单记录不存在");
        }
        permissionService.requireClassTeacher(old.getClassId());
        Result checkResult = checkClassStudent(classStudent, id);
        if (checkResult != null) {
            return checkResult;
        }
        permissionService.requireClassTeacher(classStudent.getClassId());
        classStudent.setId(id);
        int row = classStudentMapper.updateById(classStudent);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        ClassStudent old = classStudentMapper.selectById(id);
        if (old == null) {
            return Result.error("404", "学生名单记录不存在");
        }
        permissionService.requireClassTeacher(old.getClassId());
        int row = classStudentMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkClassStudent(ClassStudent classStudent, Integer excludeId) {
        if (classStudent == null) {
            return Result.error("400", "学生名单信息不能为空");
        }
        if (classStudent.getClassId() == null || classStudent.getClassId() <= 0) {
            return Result.error("400", "教学班不能为空");
        }
        if (classStudent.getStudentId() == null || classStudent.getStudentId() <= 0) {
            return Result.error("400", "学生用户不能为空");
        }
        TeachingClass teachingClass = classMapper.selectById(classStudent.getClassId());
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        try {
            permissionService.requireUserRole(classStudent.getStudentId(), "student", "学生用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        QueryWrapper<ClassStudent> duplicateQuery = new QueryWrapper<>();
        duplicateQuery.eq("class_id", classStudent.getClassId()).eq("student_id", classStudent.getStudentId());
        if (excludeId != null) {
            duplicateQuery.ne("id", excludeId);
        }
        if (classStudentMapper.selectCount(duplicateQuery) > 0) {
            return Result.error("409", "该学生已加入该教学班");
        }
        return null;
    }

    private void checkReadPermission(ClassStudent classStudent) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user)) {
            permissionService.requireClassTeacher(classStudent.getClassId());
            return;
        }
        if (!user.getUserId().equals(classStudent.getStudentId())) {
            throw new com.cupk.aiacademy.common.ForbiddenException("只能查看自己的班级");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
