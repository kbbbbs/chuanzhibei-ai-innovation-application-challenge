package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.ClassStudentDto;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
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
@RequestMapping("/class-students")
public class ClassStudentController {
    @Autowired
    private ClassStudentMapper classStudentMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ClassStudent> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ClassStudent> queryWrapper = new QueryWrapper<>();
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        if (studentId != null && studentId > 0) {
            queryWrapper.eq("student_id", studentId);
        }
        queryWrapper.orderByDesc("id");
        classStudentMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        ClassStudent classStudent = classStudentMapper.selectById(id);
        return classStudent == null ? Result.error("404", "学生名单记录不存在") : Result.success(classStudent);
    }

    @PostMapping
    public Result add(@Valid @RequestBody ClassStudentDto request) {
        ClassStudent classStudent = request.toEntity();
        Result checkResult = checkClassStudent(classStudent);
        if (checkResult != null) {
            return checkResult;
        }
        classStudent.setJoinTime(LocalDateTime.now());
        int row = classStudentMapper.insert(classStudent);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody ClassStudentDto request) {
        ClassStudent classStudent = request.toEntity();
        if (classStudentMapper.selectById(id) == null) {
            return Result.error("404", "学生名单记录不存在");
        }
        Result checkResult = checkClassStudent(classStudent);
        if (checkResult != null) {
            return checkResult;
        }
        classStudent.setId(id);
        int row = classStudentMapper.updateById(classStudent);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (classStudentMapper.selectById(id) == null) {
            return Result.error("404", "学生名单记录不存在");
        }
        int row = classStudentMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkClassStudent(ClassStudent classStudent) {
        if (classStudent == null) {
            return Result.error("400", "学生名单信息不能为空");
        }
        if (classStudent.getClassId() == null || classMapper.selectById(classStudent.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
        }
        if (classStudent.getStudentId() == null || userMapper.selectById(classStudent.getStudentId()) == null) {
            return Result.error("404", "学生用户不存在");
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
