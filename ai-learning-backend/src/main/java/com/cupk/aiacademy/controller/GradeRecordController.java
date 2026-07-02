package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
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
    public Result pages(@RequestParam(required = false) Integer studentId,
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
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        GradeRecord grade = gradeMapper.selectById(id);
        return grade == null ? Result.error("404", "成绩记录不存在") : Result.success(grade);
    }

    @PostMapping
    public Result add(@RequestBody GradeRecord grade) {
        Result checkResult = checkGrade(grade);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        grade.setGradeName(grade.getGradeName().trim());
        grade.setSourceType(grade.getSourceType().trim());
        grade.setCreateTime(now);
        grade.setUpdateTime(now);
        int row = gradeMapper.insert(grade);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody GradeRecord grade) {
        if (gradeMapper.selectById(id) == null) {
            return Result.error("404", "成绩记录不存在");
        }
        Result checkResult = checkGrade(grade);
        if (checkResult != null) {
            return checkResult;
        }
        grade.setId(id);
        grade.setGradeName(grade.getGradeName().trim());
        grade.setSourceType(grade.getSourceType().trim());
        grade.setUpdateTime(LocalDateTime.now());
        int row = gradeMapper.updateById(grade);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (gradeMapper.selectById(id) == null) {
            return Result.error("404", "成绩记录不存在");
        }
        int row = gradeMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkGrade(GradeRecord grade) {
        if (grade == null) {
            return Result.error("400", "成绩信息不能为空");
        }
        if (grade.getStudentId() == null || userMapper.selectById(grade.getStudentId()) == null) {
            return Result.error("404", "学生用户不存在");
        }
        if (grade.getClassId() == null || classMapper.selectById(grade.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
        }
        if (grade.getCourseId() == null || courseMapper.selectById(grade.getCourseId()) == null) {
            return Result.error("404", "课程不存在");
        }
        if (grade.getTeacherId() == null || userMapper.selectById(grade.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (grade.getGradeName() == null || grade.getGradeName().trim().isEmpty()) {
            return Result.error("400", "成绩名称不能为空");
        }
        if (grade.getSourceType() == null || grade.getSourceType().trim().isEmpty()) {
            return Result.error("400", "成绩来源不能为空");
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
