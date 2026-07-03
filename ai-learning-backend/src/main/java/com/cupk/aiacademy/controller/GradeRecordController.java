package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.GradeRecord;
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
@RequestMapping("/grade-records")
public class GradeRecordController {
    @Autowired
    private GradeRecordMapper gradeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private CourseMapper courseMapper;

    @GetMapping
    public ApiResponse<Page<GradeRecord>> pages(@RequestParam(required = false) Integer studentId,
                        @RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer courseId,
                        @RequestParam(defaultValue = "") String sourceType,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<GradeRecord> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<GradeRecord> queryWrapper = new QueryWrapper<>();
        if (studentId != null && studentId > 0) {
            queryWrapper.eq("student_id", studentId);
        }
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        if (courseId != null && courseId > 0) {
            queryWrapper.eq("course_id", courseId);
        }
        if (sourceType != null && !sourceType.trim().isEmpty()) {
            queryWrapper.eq("source_type", sourceType.trim());
        }
        queryWrapper.orderByDesc("id");
        gradeMapper.selectPage(page, queryWrapper);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<GradeRecord> selectById(@PathVariable Integer id) {
        GradeRecord grade = gradeMapper.selectById(id);
        if (grade == null) {
            throw new BusinessException("成绩记录不存在");
        }
        return ApiResponse.success(grade);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody GradeRecord grade) {
        checkGrade(grade);
        LocalDateTime now = LocalDateTime.now();
        grade.setGradeName(grade.getGradeName().trim());
        grade.setSourceType(grade.getSourceType().trim());
        grade.setCreateTime(now);
        grade.setUpdateTime(now);
        int row = gradeMapper.insert(grade);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody GradeRecord grade) {
        if (gradeMapper.selectById(id) == null) {
            throw new BusinessException("成绩记录不存在");
        }
        checkGrade(grade);
        grade.setId(id);
        grade.setGradeName(grade.getGradeName().trim());
        grade.setSourceType(grade.getSourceType().trim());
        grade.setUpdateTime(LocalDateTime.now());
        int row = gradeMapper.updateById(grade);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (gradeMapper.selectById(id) == null) {
            throw new BusinessException("成绩记录不存在");
        }
        int row = gradeMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkGrade(GradeRecord grade) {
        if (grade == null) {
            throw new BusinessException("成绩信息不能为空");
        }
        if (grade.getStudentId() == null || userMapper.selectById(grade.getStudentId()) == null) {
            throw new BusinessException("学生用户不存在");
        }
        if (grade.getClassId() == null || classMapper.selectById(grade.getClassId()) == null) {
            throw new BusinessException("教学班不存在");
        }
        if (grade.getCourseId() == null || courseMapper.selectById(grade.getCourseId()) == null) {
            throw new BusinessException("课程不存在");
        }
        if (grade.getTeacherId() == null || userMapper.selectById(grade.getTeacherId()) == null) {
            throw new BusinessException("教师用户不存在");
        }
        if (grade.getGradeName() == null || grade.getGradeName().trim().isEmpty()) {
            throw new BusinessException("成绩名称不能为空");
        }
        if (grade.getSourceType() == null || grade.getSourceType().trim().isEmpty()) {
            throw new BusinessException("成绩来源不能为空");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
