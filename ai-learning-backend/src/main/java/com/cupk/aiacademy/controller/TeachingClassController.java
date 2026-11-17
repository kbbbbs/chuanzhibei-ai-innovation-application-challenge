package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.TeachingClassDto;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
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
    private CourseResourceService courseResourceService;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
            java.util.List<Integer> classIds = permissionService.studentLearningClassIds(loginUser.getUserId());
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
        } else {
            permissionService.requireStudentLearningClass(id, teachingClass.getCourseId(), user.getUserId());
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
    @org.springframework.transaction.annotation.Transactional
    public Result add(@Valid @RequestBody TeachingClassDto request) {
        permissionService.requireAdmin();
        TeachingClass teachingClass = request.toEntity();
        lockCourse(teachingClass.getCourseId());
        teachingClass.setCourseOpen(false);
        applyTeachingClassTeacher(teachingClass, null);
        Result checkResult = checkTeachingClass(teachingClass);
        if (checkResult != null) {
            return checkResult;
        }
        stampTeachingClassForCreate(teachingClass);
        int row = classMapper.insert(teachingClass);
        if (row > 0) bumpCourse(teachingClass.getCourseId());
        return row > 0 ? Result.success(teachingClass) : Result.error();
    }

    @PutMapping("/teaching-classes/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Result update(@PathVariable Integer id, @Valid @RequestBody TeachingClassDto request) {
        permissionService.requireAdmin();
        TeachingClass oldClass = classMapper.selectById(id);
        if (oldClass == null) {
            return Result.error("404", "教学班不存在");
        }
        TeachingClass teachingClass = request.toEntity();
        Integer previousCourseId = oldClass.getCourseId();
        java.util.stream.Stream.of(oldClass.getCourseId(), teachingClass.getCourseId()).filter(java.util.Objects::nonNull).distinct().sorted().forEach(this::lockCourse);
        oldClass = classMapper.selectForUpdate(id);
        if (oldClass == null || !java.util.Objects.equals(previousCourseId, oldClass.getCourseId())) {
            return Result.error("409", "教学班归属已更新，请刷新后重试");
        }
        if (!java.util.Objects.equals(oldClass.getCourseId(), teachingClass.getCourseId()) && hasBusinessHistory(id)) {
            return Result.error("409", "该教学班已有学习或考核记录，请新建教学班承接其他课程");
        }
        teachingClass.setCourseOpen(java.util.Objects.equals(oldClass.getCourseId(), teachingClass.getCourseId())
                && Boolean.TRUE.equals(oldClass.getCourseOpen()));
        applyTeachingClassTeacher(teachingClass, oldClass);
        Result checkResult = checkTeachingClass(teachingClass);
        if (checkResult != null) {
            return checkResult;
        }
        stampTeachingClassForUpdate(teachingClass, id);
        int row = classMapper.updateById(teachingClass);
        if (row > 0) {
            bumpCourse(oldClass.getCourseId());
            if (!java.util.Objects.equals(oldClass.getCourseId(), teachingClass.getCourseId())) bumpCourse(teachingClass.getCourseId());
        }
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/teaching-classes/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Result delete(@PathVariable Integer id) {
        permissionService.requireAdmin();
        TeachingClass teachingClass = classMapper.selectById(id);
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        boolean courseExists = !jdbc.queryForList("SELECT id FROM t_course WHERE id=? FOR UPDATE",
                Integer.class, teachingClass.getCourseId()).isEmpty();
        TeachingClass current = classMapper.selectForUpdate(id);
        if (current == null || !java.util.Objects.equals(teachingClass.getCourseId(), current.getCourseId())) {
            return Result.error("409", "教学班归属已更新，请刷新后重试");
        }
        if (hasBusinessHistory(id)) return Result.error("409", "该教学班已有学习或考核记录，请停用教学班以保留历史");
        jdbc.update("DELETE FROM t_class_student WHERE class_id=?", id);
        int row = classMapper.deleteById(id);
        if (row > 0 && courseExists) bumpCourse(teachingClass.getCourseId());
        return row > 0 ? Result.success() : Result.error();
    }

    private AuthUtils.LoginUser parseLoginUser(String authorization) {
        try {
            return authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return null;
        }
    }

    private void lockCourse(Integer courseId) {
        if (courseId != null) jdbc.queryForObject("SELECT id FROM t_course WHERE id=? FOR UPDATE", Integer.class, courseId);
    }

    private void bumpCourse(Integer courseId) {
        if (courseId != null) jdbc.update("UPDATE t_course SET workflow_version=COALESCE(workflow_version,0)+1 WHERE id=?", courseId);
    }

    private boolean hasBusinessHistory(Integer classId) {
        for (String table : java.util.List.of("t_learning_progress", "t_learning_record", "t_grade_record", "t_exam_session", "t_homework_session", "t_homework", "t_grade_import_log")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE class_id=?", Long.class, classId) > 0) return true;
        }
        return false;
    }

    private void applyTeachingClassTeacher(TeachingClass teachingClass, TeachingClass oldClass) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            teachingClass.setTeacherId(user.getUserId());
        } else if (teachingClass.getTeacherId() == null && oldClass != null) {
            teachingClass.setTeacherId(oldClass.getTeacherId());
        }
    }

    private void stampTeachingClassForCreate(TeachingClass teachingClass) {
        LocalDateTime now = LocalDateTime.now();
        teachingClass.setClassName(teachingClass.getClassName().trim());
        teachingClass.setCreateTime(now);
        teachingClass.setUpdateTime(now);
    }

    private void stampTeachingClassForUpdate(TeachingClass teachingClass, Integer id) {
        teachingClass.setId(id);
        teachingClass.setClassName(teachingClass.getClassName().trim());
        teachingClass.setUpdateTime(LocalDateTime.now());
    }

    private Result checkTeachingClass(TeachingClass teachingClass) {
        if (teachingClass == null) {
            return Result.error("400", "教学班信息不能为空");
        }
        Course course = teachingClass.getCourseId() == null ? null : courseMapper.selectById(teachingClass.getCourseId());
        if (teachingClass.getCourseId() != null && course == null) {
            return Result.error("404", "课程不存在");
        }
        if (teachingClass.getTeacherId() == null || teachingClass.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        try {
            permissionService.requireUserRole(teachingClass.getTeacherId(), "teacher", "教师用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        if (course != null && course.getTeacherId() != null && !course.getTeacherId().equals(teachingClass.getTeacherId())) {
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
