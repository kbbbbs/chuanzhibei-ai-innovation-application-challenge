package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.CourseDto;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
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
@RequestMapping("/courses")
public class CourseController {
    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseCategoryMapper categoryMapper;

    @Autowired
    private UserMapper userMapper;

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
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        if (categoryId != null && categoryId > 0) {
            queryWrapper.eq("category_id", categoryId);
        }
        if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        queryWrapper.orderByDesc("id");
        courseMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        return course == null ? Result.error("404", "课程不存在") : Result.success(course);
    }

    @PostMapping
    public Result add(@Valid @RequestBody CourseDto request) {
        Course course = request.toEntity();
        Result checkResult = checkCourse(course);
        if (checkResult != null) {
            return checkResult;
        }
        course.setTitle(course.getTitle().trim());
        LocalDateTime now = LocalDateTime.now();
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        int row = courseMapper.insert(course);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody CourseDto request) {
        Course course = request.toEntity();
        if (courseMapper.selectById(id) == null) {
            return Result.error("404", "课程不存在");
        }
        Result checkResult = checkCourse(course);
        if (checkResult != null) {
            return checkResult;
        }
        course.setId(id);
        course.setTitle(course.getTitle().trim());
        course.setUpdatedAt(LocalDateTime.now());
        int row = courseMapper.updateById(course);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (courseMapper.selectById(id) == null) {
            return Result.error("404", "课程不存在");
        }
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
        if (course.getTeacherId() != null && userMapper.selectById(course.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        return null;
    }
}
