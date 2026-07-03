package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    @Autowired
    private CourseCategoryMapper categoryMapper;

    @Autowired
    private CourseMapper courseMapper;

    @GetMapping
    public ApiResponse<Object> list() {
        QueryWrapper<CourseCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("id");
        return ApiResponse.success(categoryMapper.selectList(queryWrapper));
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody CourseCategory category) {
        checkCategory(category);
        category.setName(category.getName().trim());
        fixSortOrder(category);
        LocalDateTime now = LocalDateTime.now();
        category.setCreateTime(now);
        category.setUpdateTime(now);
        int row = categoryMapper.insert(category);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody CourseCategory category) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        checkCategory(category);
        category.setId(id);
        category.setName(category.getName().trim());
        fixSortOrder(category);
        category.setUpdateTime(LocalDateTime.now());
        int row = categoryMapper.updateById(category);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> delete(@PathVariable Integer id, @RequestParam(required = false) Boolean cascade) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException("分类不存在");
        }

        QueryWrapper<Course> courseQuery = new QueryWrapper<>();
        courseQuery.eq("category_id", id);
        Long courseCount = courseMapper.selectCount(courseQuery);

        if (courseCount > 0 && !Boolean.TRUE.equals(cascade)) {
            throw new BusinessException("该分类下还有" + courseCount + "门课程，请先处理课程或选择级联删除");
        }

        int deletedCourses = 0;
        if (courseCount > 0) {
            deletedCourses = courseMapper.delete(courseQuery);
        }

        int row = categoryMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("deletedCourses", deletedCourses);
        return ApiResponse.success(data);
    }

    private void checkCategory(CourseCategory category) {
        if (category == null) {
            throw new BusinessException("分类信息不能为空");
        }
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
    }

    private void fixSortOrder(CourseCategory category) {
        if (category.getSortOrder() == null || category.getSortOrder() <= 0) {
            category.setSortOrder(null);
        }
    }
}
