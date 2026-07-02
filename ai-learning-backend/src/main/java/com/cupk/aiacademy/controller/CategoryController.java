package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.Result;
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
    public Result list() {
        QueryWrapper<CourseCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("id");
        return Result.success(categoryMapper.selectList(queryWrapper));
    }

    @PostMapping
    public Result add(@RequestBody CourseCategory category) {
        Result checkResult = checkCategory(category);
        if (checkResult != null) {
            return checkResult;
        }
        category.setName(category.getName().trim());
        fixSortOrder(category);
        LocalDateTime now = LocalDateTime.now();
        category.setCreateTime(now);
        category.setUpdateTime(now);
        int row = categoryMapper.insert(category);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody CourseCategory category) {
        if (categoryMapper.selectById(id) == null) {
            return Result.error("404", "分类不存在");
        }
        Result checkResult = checkCategory(category);
        if (checkResult != null) {
            return checkResult;
        }
        category.setId(id);
        category.setName(category.getName().trim());
        fixSortOrder(category);
        category.setUpdateTime(LocalDateTime.now());
        int row = categoryMapper.updateById(category);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result delete(@PathVariable Integer id, @RequestParam(required = false) Boolean cascade) {
        if (categoryMapper.selectById(id) == null) {
            return Result.error("404", "分类不存在");
        }

        QueryWrapper<Course> courseQuery = new QueryWrapper<>();
        courseQuery.eq("category_id", id);
        Long courseCount = courseMapper.selectCount(courseQuery);

        if (courseCount > 0 && !Boolean.TRUE.equals(cascade)) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasCourses", true);
            data.put("courseCount", courseCount);
            return Result.error("409", "该分类下还有课程，请先处理课程或选择级联删除", data);
        }

        int deletedCourses = 0;
        if (courseCount > 0) {
            deletedCourses = courseMapper.delete(courseQuery);
        }

        int row = categoryMapper.deleteById(id);
        if (row <= 0) {
            return Result.error();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("deletedCourses", deletedCourses);
        return Result.success(data);
    }

    private Result checkCategory(CourseCategory category) {
        if (category == null) {
            return Result.error("400", "分类信息不能为空");
        }
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            return Result.error("400", "分类名称不能为空");
        }
        return null;
    }

    private void fixSortOrder(CourseCategory category) {
        if (category.getSortOrder() == null || category.getSortOrder() <= 0) {
            category.setSortOrder(null);
        }
    }
}
