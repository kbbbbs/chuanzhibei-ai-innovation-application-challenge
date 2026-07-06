package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.CourseDto;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/courses")
public class CourseController {
    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseCategoryMapper categoryMapper;

    @Autowired(required = false)
    private AuthUtils authUtils;

    @Autowired
    private CourseResourceService courseResourceService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result pages(@RequestParam(defaultValue = "") String title,
                        @RequestParam(required = false) Integer categoryId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "5") Integer pageSize) {
        pageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        pageSize = pageSize == null || pageSize <= 0 ? 5 : pageSize;

        Page<Course> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser loginUser = permissionService.currentUser();
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        if (categoryId != null && categoryId > 0) {
            queryWrapper.eq("category_id", categoryId);
        }
        if (authUtils.isTeacher(loginUser)) {
            queryWrapper.eq("teacher_id", loginUser.getUserId());
        } else if (authUtils.isStudent(loginUser)) {
            queryWrapper.eq("status", 1);
        } else if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        queryWrapper.orderByDesc("id");
        courseMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/my")
    public Result myCourses(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @RequestParam(defaultValue = "1") Integer pageNum,
                            @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }
        return Result.success(courseResourceService.listMyCourses(loginUser, pageNum, pageSize));
    }


    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        return course == null ? Result.error("404", "课程不存在") : Result.success(course);
    }

    @PostMapping
    public Result add(@Valid @RequestBody CourseDto request) {
        permissionService.requireTeacherOrAdmin();
        Course course = request.toEntity();
        applyCourseTeacher(course, null);
        Result checkResult = checkCourse(course);
        if (checkResult != null) {
            return checkResult;
        }
        stampCourseForCreate(course);
        int row = courseMapper.insert(course);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody CourseDto request) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return Result.error("404", "课程不存在");
        }

        permissionService.requireCourseTeacher(course);

        Course newCourse = request.toEntity();
        applyCourseTeacher(newCourse, course);
        Result checkResult = checkCourse(newCourse);
        if (checkResult != null) {
            return checkResult;
        }
        stampCourseForUpdate(newCourse, id);
        int row = courseMapper.updateById(newCourse);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return Result.error("404", "课程不存在");
        }

        permissionService.requireCourseTeacher(course);

        int row = courseMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}/view")
    public Result addViewCount(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return Result.error("404", "课程不存在");
        }
        Integer viewCount = course.getViewCount() == null ? 0 : course.getViewCount();
        course.setViewCount(viewCount + 1);
        course.setUpdatedAt(LocalDateTime.now());
        int row = courseMapper.updateById(course);
        return row > 0 ? Result.success() : Result.error();
    }

    @GetMapping("/{courseId}/my-grade-records")
    public Result myGradeRecords(@PathVariable Integer courseId,
                                 @RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam(defaultValue = "") String sourceType,
                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }
        return Result.success(courseResourceService.listMyGradeRecords(courseId, loginUser, sourceType, pageNum, pageSize));
    }

    @GetMapping("/{courseId}/exam-papers")
    public Result examPapers(@PathVariable Integer courseId,
                             @RequestParam(required = false) Integer status,
                             @RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(courseResourceService.listCourseExamPapers(courseId, status, pageNum, pageSize, UserContext.get()));
    }

    @GetMapping("/{courseId}/my-learning-progress")
    public Result myLearningProgress(@PathVariable Integer courseId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestParam(required = false) Integer classId) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }
        return Result.success(courseResourceService.listMyCourseLearningProgress(courseId, classId, loginUser));
    }

    private AuthUtils.LoginUser parseLoginUser(String authorization) {
        try {
            return authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return null;
        }
    }

    private void stampCourseForCreate(Course course) {
        course.setTitle(course.getTitle().trim());
        LocalDateTime now = LocalDateTime.now();
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
    }

    private void stampCourseForUpdate(Course course, Integer id) {
        course.setId(id);
        course.setTitle(course.getTitle().trim());
        course.setUpdatedAt(LocalDateTime.now());
    }

    private void applyCourseTeacher(Course course, Course oldCourse) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            course.setTeacherId(user.getUserId());
        } else if (course.getTeacherId() == null && oldCourse != null) {
            course.setTeacherId(oldCourse.getTeacherId());
        }
    }

    private Result checkCourse(Course course) {
        if (course == null) {
            return Result.error("400", "课程信息不能为空");
        }
        if (course.getTitle() == null || course.getTitle().trim().isEmpty()) {
            return Result.error("400", "课程名称不能为空");
        }
        if (course.getCategoryId() == null || course.getCategoryId() <= 0) {
            return Result.error("400", "请选择课程分类");
        }
        if (categoryMapper.selectById(course.getCategoryId()) == null) {
            return Result.error("404", "课程分类不存在");
        }
        if (course.getTeacherId() == null || course.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        try {
            permissionService.requireUserRole(course.getTeacherId(), "teacher", "教师用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        return null;
    }

}
