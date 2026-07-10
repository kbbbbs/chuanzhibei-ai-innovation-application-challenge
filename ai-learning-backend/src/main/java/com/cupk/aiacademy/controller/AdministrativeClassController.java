package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.AdministrativeClassDto;
import com.cupk.aiacademy.dto.AdministrativeClassStudentDto;
import com.cupk.aiacademy.mapper.AdministrativeClassMapper;
import com.cupk.aiacademy.mapper.AdministrativeClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.AdministrativeClass;
import com.cupk.aiacademy.pojo.AdministrativeClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
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
@RequestMapping("/administrative-classes")
public class AdministrativeClassController {
    private final AdministrativeClassMapper classMapper;
    private final AdministrativeClassStudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    public AdministrativeClassController(AdministrativeClassMapper classMapper,
                                         AdministrativeClassStudentMapper studentMapper,
                                         CourseMapper courseMapper,
                                         UserMapper userMapper,
                                         PermissionService permissionService) {
        this.classMapper = classMapper;
        this.studentMapper = studentMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer courseId,
                        @RequestParam(defaultValue = "") String classCode,
                        @RequestParam(defaultValue = "") String className,
                        @RequestParam(defaultValue = "") String grade,
                        @RequestParam(defaultValue = "") String major,
                        @RequestParam(defaultValue = "") String keyword,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        permissionService.requireAdmin();
        Page<AdministrativeClass> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<AdministrativeClass> query = new QueryWrapper<>();
        if (courseId != null && courseId > 0) {
            query.eq("course_id", courseId);
        }
        if (classCode != null && !classCode.trim().isEmpty()) {
            query.like("class_code", classCode.trim());
        }
        if (className != null && !className.trim().isEmpty()) {
            query.like("class_name", className.trim());
        }
        if (grade != null && !grade.trim().isEmpty()) {
            query.like("grade", grade.trim());
        }
        if (major != null && !major.trim().isEmpty()) {
            query.like("major", major.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String term = keyword.trim();
            query.and(wrapper -> wrapper.like("class_code", term)
                    .or()
                    .like("class_name", term)
                    .or()
                    .like("grade", term)
                    .or()
                    .like("major", term)
                    .or()
                    .like("description", term));
        }
        query.orderByDesc("id");
        classMapper.selectPage(page, query);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        permissionService.requireAdmin();
        AdministrativeClass administrativeClass = classMapper.selectById(id);
        return administrativeClass == null ? Result.error("404", "行政班不存在") : Result.success(administrativeClass);
    }

    @GetMapping("/{classId}/students")
    public Result listStudents(@PathVariable Integer classId) {
        permissionService.requireAdmin();
        if (classMapper.selectById(classId) == null) {
            return Result.error("404", "行政班不存在");
        }
        QueryWrapper<AdministrativeClassStudent> query = new QueryWrapper<>();
        query.eq("administrative_class_id", classId).orderByDesc("id");
        return Result.success(studentMapper.selectList(query));
    }

    @PostMapping
    public Result add(@Valid @RequestBody AdministrativeClassDto request) {
        permissionService.requireAdmin();
        AdministrativeClass administrativeClass = request.toEntity();
        Result checkResult = checkAdministrativeClass(administrativeClass, null);
        if (checkResult != null) {
            return checkResult;
        }
        stampForCreate(administrativeClass);
        int row = classMapper.insert(administrativeClass);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody AdministrativeClassDto request) {
        permissionService.requireAdmin();
        if (classMapper.selectById(id) == null) {
            return Result.error("404", "行政班不存在");
        }
        AdministrativeClass administrativeClass = request.toEntity();
        Result checkResult = checkAdministrativeClass(administrativeClass, id);
        if (checkResult != null) {
            return checkResult;
        }
        stampForUpdate(administrativeClass, id);
        int row = classMapper.updateById(administrativeClass);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        permissionService.requireAdmin();
        if (classMapper.selectById(id) == null) {
            return Result.error("404", "行政班不存在");
        }
        int row = classMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    @PostMapping("/{classId}/students")
    public Result addStudent(@PathVariable Integer classId, @Valid @RequestBody AdministrativeClassStudentDto request) {
        permissionService.requireAdmin();
        AdministrativeClassStudent student = request.toEntity(classId);
        Result checkResult = checkClassStudent(student, null);
        if (checkResult != null) {
            return checkResult;
        }
        student.setJoinTime(LocalDateTime.now());
        int row = studentMapper.insert(student);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{classId}/students/{id}")
    public Result updateStudent(@PathVariable Integer classId,
                                @PathVariable Integer id,
                                @Valid @RequestBody AdministrativeClassStudentDto request) {
        permissionService.requireAdmin();
        AdministrativeClassStudent old = studentMapper.selectById(id);
        if (old == null || !classId.equals(old.getAdministrativeClassId())) {
            return Result.error("404", "行政班学生记录不存在");
        }
        AdministrativeClassStudent student = request.toEntity(classId);
        Result checkResult = checkClassStudent(student, id);
        if (checkResult != null) {
            return checkResult;
        }
        student.setId(id);
        student.setJoinTime(old.getJoinTime());
        int row = studentMapper.updateById(student);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{classId}/students/{id}")
    public Result deleteStudent(@PathVariable Integer classId, @PathVariable Integer id) {
        permissionService.requireAdmin();
        AdministrativeClassStudent old = studentMapper.selectById(id);
        if (old == null || !classId.equals(old.getAdministrativeClassId())) {
            return Result.error("404", "行政班学生记录不存在");
        }
        int row = studentMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkAdministrativeClass(AdministrativeClass administrativeClass, Integer excludeId) {
        if (administrativeClass == null) {
            return Result.error("400", "行政班信息不能为空");
        }
        Course course = administrativeClass.getCourseId() == null ? null : courseMapper.selectById(administrativeClass.getCourseId());
        if (course == null) {
            return Result.error("404", "课程不存在");
        }
        if (administrativeClass.getClassCode() == null || administrativeClass.getClassCode().trim().isEmpty()) {
            return Result.error("400", "行政班编号不能为空");
        }
        if (administrativeClass.getClassName() == null || administrativeClass.getClassName().trim().isEmpty()) {
            return Result.error("400", "行政班名称不能为空");
        }
        QueryWrapper<AdministrativeClass> duplicateQuery = new QueryWrapper<>();
        duplicateQuery.eq("class_code", administrativeClass.getClassCode().trim());
        if (excludeId != null) {
            duplicateQuery.ne("id", excludeId);
        }
        if (classMapper.selectCount(duplicateQuery) > 0) {
            return Result.error("409", "行政班编号已存在");
        }
        return null;
    }

    private Result checkClassStudent(AdministrativeClassStudent student, Integer excludeId) {
        if (student == null) {
            return Result.error("400", "学生名单信息不能为空");
        }
        if (student.getAdministrativeClassId() == null || student.getAdministrativeClassId() <= 0) {
            return Result.error("400", "行政班不能为空");
        }
        if (classMapper.selectById(student.getAdministrativeClassId()) == null) {
            return Result.error("404", "行政班不存在");
        }
        try {
            permissionService.requireUserRole(student.getStudentId(), "student", "学生用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        QueryWrapper<AdministrativeClassStudent> duplicateQuery = new QueryWrapper<>();
        duplicateQuery.eq("administrative_class_id", student.getAdministrativeClassId())
                .eq("student_id", student.getStudentId());
        if (excludeId != null) {
            duplicateQuery.ne("id", excludeId);
        }
        if (studentMapper.selectCount(duplicateQuery) > 0) {
            return Result.error("409", "该学生已加入该行政班");
        }
        return null;
    }

    private void stampForCreate(AdministrativeClass administrativeClass) {
        LocalDateTime now = LocalDateTime.now();
        administrativeClass.setClassCode(administrativeClass.getClassCode().trim());
        administrativeClass.setClassName(administrativeClass.getClassName().trim());
        administrativeClass.setStatus(administrativeClass.getStatus() == null ? 1 : administrativeClass.getStatus());
        administrativeClass.setCreateTime(now);
        administrativeClass.setUpdateTime(now);
    }

    private void stampForUpdate(AdministrativeClass administrativeClass, Integer id) {
        administrativeClass.setId(id);
        administrativeClass.setClassCode(administrativeClass.getClassCode().trim());
        administrativeClass.setClassName(administrativeClass.getClassName().trim());
        administrativeClass.setStatus(administrativeClass.getStatus() == null ? 1 : administrativeClass.getStatus());
        administrativeClass.setUpdateTime(LocalDateTime.now());
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
