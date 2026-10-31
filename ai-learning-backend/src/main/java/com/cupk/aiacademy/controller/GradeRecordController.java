package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.GradeRecordDto;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.GradeRecord;
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
import java.util.Set;

@RestController
@RequestMapping("/grade-records")
public class GradeRecordController {
    private static final Set<String> VALID_SOURCE_TYPES = Set.of("homework", "exam", "manual", "import");

    @Autowired
    private GradeRecordMapper gradeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer studentId,
                        @RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer courseId,
                        @RequestParam(defaultValue = "") String sourceType,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<GradeRecord> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<GradeRecord> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isStudent(user)) {
            queryWrapper.eq("student_id", user.getUserId());
        } else if (authUtils.isTeacher(user)) {
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
        } else {
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
        }
        if (courseId != null && courseId > 0) {
            queryWrapper.eq("course_id", courseId);
        }
        if (sourceType != null && !sourceType.trim().isEmpty()) {
            queryWrapper.eq("source_type", sourceType.trim());
        }
        queryWrapper.orderByDesc("id");
        gradeMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        GradeRecord grade = gradeMapper.selectById(id);
        if (grade == null) {
            return Result.error("404", "成绩记录不存在");
        }
        checkGradeReadPermission(grade);
        return Result.success(grade);
    }

    @PostMapping
    public Result add(@Valid @RequestBody GradeRecordDto request) {
        permissionService.requireTeacherOrAdmin();
        GradeRecord grade = request.toEntity();
        applyGradeTeacher(grade);
        Result checkResult = checkGrade(grade);
        if (checkResult != null) {
            return checkResult;
        }
        stampGradeForCreate(grade);
        int row = gradeMapper.insert(grade);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody GradeRecordDto request) {
        GradeRecord grade = request.toEntity();
        GradeRecord old = gradeMapper.selectById(id);
        if (old == null) {
            return Result.error("404", "成绩记录不存在");
        }
        checkGradeWritePermission(old);
        applyGradeTeacher(grade);
        Result checkResult = checkGrade(grade);
        if (checkResult != null) {
            return checkResult;
        }
        stampGradeForUpdate(grade, id);
        int row = gradeMapper.updateById(grade);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        GradeRecord grade = gradeMapper.selectById(id);
        if (grade == null) {
            return Result.error("404", "成绩记录不存在");
        }
        checkGradeWritePermission(grade);
        int row = gradeMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkGrade(GradeRecord grade) {
        if (grade == null) {
            return Result.error("400", "成绩信息不能为空");
        }
        if (grade.getStudentId() == null || grade.getStudentId() <= 0) {
            return Result.error("400", "学生用户不能为空");
        }
        if (grade.getClassId() == null || grade.getClassId() <= 0) {
            return Result.error("400", "教学班不能为空");
        }
        if (grade.getCourseId() == null || grade.getCourseId() <= 0) {
            return Result.error("400", "课程不能为空");
        }
        if (grade.getTeacherId() == null || grade.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        if (userMapper.selectById(grade.getStudentId()) == null) {
            return Result.error("404", "学生用户不存在");
        }
        TeachingClass teachingClass = classMapper.selectById(grade.getClassId());
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        if (courseMapper.selectById(grade.getCourseId()) == null) {
            return Result.error("404", "课程不存在");
        }
        if (userMapper.selectById(grade.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (!teachingClass.getCourseId().equals(grade.getCourseId())) {
            return Result.error("400", "成绩课程与教学班课程不一致");
        }
        if (!teachingClass.getTeacherId().equals(grade.getTeacherId())) {
            return Result.error("400", "成绩教师与教学班教师不一致");
        }
        if (!permissionService.isStudentInClass(grade.getClassId(), grade.getStudentId())) {
            return Result.error("400", "学生未加入该教学班");
        }
        if (grade.getGradeName() == null || grade.getGradeName().trim().isEmpty()) {
            return Result.error("400", "成绩名称不能为空");
        }
        if (grade.getSourceType() == null || grade.getSourceType().trim().isEmpty()) {
            return Result.error("400", "成绩来源不能为空");
        }
        if (!VALID_SOURCE_TYPES.contains(grade.getSourceType().trim())) {
            return Result.error("400", "成绩来源不合法");
        }
        return null;
    }

    private void applyGradeTeacher(GradeRecord grade) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            grade.setTeacherId(user.getUserId());
        } else if (grade.getClassId() != null && (grade.getTeacherId() == null || grade.getTeacherId() <= 0)) {
            TeachingClass teachingClass = classMapper.selectById(grade.getClassId());
            if (teachingClass != null) {
                grade.setTeacherId(teachingClass.getTeacherId());
            }
        }
    }

    private void stampGradeForCreate(GradeRecord grade) {
        LocalDateTime now = LocalDateTime.now();
        grade.setGradeName(grade.getGradeName().trim());
        grade.setSourceType(grade.getSourceType().trim());
        grade.setCreateTime(now);
        grade.setUpdateTime(now);
    }

    private void stampGradeForUpdate(GradeRecord grade, Integer id) {
        grade.setId(id);
        grade.setGradeName(grade.getGradeName().trim());
        grade.setSourceType(grade.getSourceType().trim());
        grade.setUpdateTime(LocalDateTime.now());
    }

    private void checkGradeReadPermission(GradeRecord grade) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isStudent(user)) {
            if (!user.getUserId().equals(grade.getStudentId())) {
                throw new com.cupk.aiacademy.common.ForbiddenException("只能查看自己的成绩");
            }
            return;
        }
        permissionService.requireClassTeacher(grade.getClassId());
    }

    private void checkGradeWritePermission(GradeRecord grade) {
        permissionService.requireTeacherOrAdmin();
        if (!authUtils.isAdmin(permissionService.currentUser())) {
            permissionService.requireClassTeacher(grade.getClassId());
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
