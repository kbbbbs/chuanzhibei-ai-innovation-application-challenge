package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.TeachingClassDto;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.service.CourseResourceService;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class TeachingClassController {
    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private ClassStudentMapper classStudentMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CourseResourceService courseResourceService;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/teaching-classes")
    public Result pages(@RequestParam(required = false) Integer courseId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "") String className,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<TeachingClass> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<TeachingClass> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        if (courseId != null && courseId > 0) {
            queryWrapper.eq("course_id", courseId);
        }
        if (authUtils.isTeacher(loginUser)) {
            queryWrapper.eq("teacher_id", loginUser.getUserId());
        } else if (authUtils.isStudent(loginUser)) {
            java.util.List<Integer> classIds = permissionService.studentClassIds(loginUser.getUserId());
            if (classIds.isEmpty()) {
                return Result.success(page);
            }
            queryWrapper.in("id", classIds);
        } else if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        if (className != null && !className.trim().isEmpty()) {
            queryWrapper.like("class_name", className.trim());
        }
        queryWrapper.orderByDesc("id");
        classMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/teaching-classes/my")
    public Result myTeachingClasses(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(required = false) Integer status,
                                    @RequestParam(defaultValue = "1") Integer pageNum,
                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }
        return Result.success(courseResourceService.listMyTeachingClasses(loginUser, status, pageNum, pageSize));
    }

    @GetMapping("/teaching-classes/{id}")
    public Result selectById(@PathVariable Integer id) {
        TeachingClass teachingClass = classMapper.selectById(id);
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user) || authUtils.isAdmin(user)) {
            permissionService.requireClassTeacher(teachingClass);
        } else if (!permissionService.isStudentInClass(id, user.getUserId())) {
            return Result.error("403", "无权限查看该教学班");
        }
        return Result.success(teachingClass);
    }

    @GetMapping("/teaching-classes/{classId}/students")
    public Result listStudents(@PathVariable Integer classId) {
        if (classMapper.selectById(classId) == null) {
            return Result.error("404", "教学班不存在");
        }
        permissionService.requireClassTeacher(classId);
        QueryWrapper<ClassStudent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("class_id", classId).orderByDesc("id");
        return Result.success(classStudentMapper.selectList(queryWrapper));
    }

    @PostMapping("/teaching-classes")
    public Result add(@Valid @RequestBody TeachingClassDto request) {
        permissionService.requireTeacherOrAdmin();
        TeachingClass teachingClass = request.toEntity();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            teachingClass.setTeacherId(user.getUserId());
        }
        Result checkResult = checkTeachingClass(teachingClass);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        teachingClass.setClassName(teachingClass.getClassName().trim());
        teachingClass.setCreateTime(now);
        teachingClass.setUpdateTime(now);
        int row = classMapper.insert(teachingClass);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/teaching-classes/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody TeachingClassDto request) {
        TeachingClass oldClass = classMapper.selectById(id);
        if (oldClass == null) {
            return Result.error("404", "教学班不存在");
        }
        permissionService.requireClassTeacher(oldClass);
        TeachingClass teachingClass = request.toEntity();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            teachingClass.setTeacherId(user.getUserId());
        } else if (teachingClass.getTeacherId() == null) {
            teachingClass.setTeacherId(oldClass.getTeacherId());
        }
        Result checkResult = checkTeachingClass(teachingClass);
        if (checkResult != null) {
            return checkResult;
        }
        teachingClass.setId(id);
        teachingClass.setClassName(teachingClass.getClassName().trim());
        teachingClass.setUpdateTime(LocalDateTime.now());
        int row = classMapper.updateById(teachingClass);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/teaching-classes/{id}")
    public Result delete(@PathVariable Integer id) {
        TeachingClass teachingClass = classMapper.selectById(id);
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        permissionService.requireClassTeacher(teachingClass);
        int row = classMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private AuthUtils.LoginUser parseLoginUser(String authorization) {
        try {
            return authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return null;
        }
    }

    private Result checkTeachingClass(TeachingClass teachingClass) {
        if (teachingClass == null) {
            return Result.error("400", "教学班信息不能为空");
        }
        if (teachingClass.getCourseId() == null || courseMapper.selectById(teachingClass.getCourseId()) == null) {
            return Result.error("404", "课程不存在");
        }
        com.cupk.aiacademy.pojo.Course course = courseMapper.selectById(teachingClass.getCourseId());
        if (teachingClass.getTeacherId() == null || teachingClass.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        try {
            permissionService.requireUserRole(teachingClass.getTeacherId(), "teacher", "教师用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        if (course.getTeacherId() != null && !course.getTeacherId().equals(teachingClass.getTeacherId())) {
            return Result.error("400", "教学班教师必须与课程教师一致");
        }
        if (teachingClass.getClassName() == null || teachingClass.getClassName().trim().isEmpty()) {
            return Result.error("400", "教学班名称不能为空");
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
