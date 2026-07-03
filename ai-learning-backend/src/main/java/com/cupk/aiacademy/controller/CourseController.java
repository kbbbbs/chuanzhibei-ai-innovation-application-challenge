package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
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
    public ApiResponse<Page<Course>> pages(@RequestParam(defaultValue = "") String title,
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
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<Course> selectById(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return ApiResponse.success(course);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody Course course) {
        checkCourse(course);
        course.setTitle(course.getTitle().trim());
        LocalDateTime now = LocalDateTime.now();
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        int row = courseMapper.insert(course);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody Course course) {
        if (courseMapper.selectById(id) == null) {
            throw new BusinessException("课程不存在");
        }
        checkCourse(course);
        course.setId(id);
        course.setTitle(course.getTitle().trim());
        course.setUpdatedAt(LocalDateTime.now());
        int row = courseMapper.updateById(course);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (courseMapper.selectById(id) == null) {
            throw new BusinessException("课程不存在");
        }
        int row = courseMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/view")
    public ApiResponse<Void> addViewCount(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        Integer viewCount = course.getViewCount() == null ? 0 : course.getViewCount();
        course.setViewCount(viewCount + 1);
        course.setUpdatedAt(LocalDateTime.now());
        int row = courseMapper.updateById(course);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    private void checkCourse(Course course) {
        if (course == null) {
            throw new BusinessException("课程信息不能为空");
        }
        if (course.getTitle() == null || course.getTitle().trim().isEmpty()) {
            throw new BusinessException("课程名称不能为空");
        }
        if (course.getCategoryId() == null || course.getCategoryId() <= 0) {
            throw new BusinessException("请选择课程分类");
        }
        if (categoryMapper.selectById(course.getCategoryId()) == null) {
            throw new BusinessException("课程分类不存在");
        }
        if (course.getTeacherId() != null && userMapper.selectById(course.getTeacherId()) == null) {
            throw new BusinessException("教师用户不存在");
        }
    }
}
