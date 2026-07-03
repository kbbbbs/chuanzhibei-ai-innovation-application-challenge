package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
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
@RequestMapping("/homework-submits")
public class HomeworkSubmitController {
    @Autowired
    private HomeworkSubmitMapper submitMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public ApiResponse<Page<HomeworkSubmit>> pages(@RequestParam(required = false) Integer homeworkId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<HomeworkSubmit> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<HomeworkSubmit> queryWrapper = new QueryWrapper<>();
        if (homeworkId != null && homeworkId > 0) {
            queryWrapper.eq("homework_id", homeworkId);
        }
        if (studentId != null && studentId > 0) {
            queryWrapper.eq("student_id", studentId);
        }
        queryWrapper.orderByDesc("id");
        submitMapper.selectPage(page, queryWrapper);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<HomeworkSubmit> selectById(@PathVariable Integer id) {
        HomeworkSubmit submit = submitMapper.selectById(id);
        if (submit == null) {
            throw new BusinessException("作业提交不存在");
        }
        return ApiResponse.success(submit);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody HomeworkSubmit submit) {
        checkSubmit(submit);
        LocalDateTime now = LocalDateTime.now();
        if (submit.getSubmitTime() == null) {
            submit.setSubmitTime(now);
        }
        submit.setCreateTime(now);
        submit.setUpdateTime(now);
        int row = submitMapper.insert(submit);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody HomeworkSubmit submit) {
        if (submitMapper.selectById(id) == null) {
            throw new BusinessException("作业提交不存在");
        }
        checkSubmit(submit);
        submit.setId(id);
        submit.setUpdateTime(LocalDateTime.now());
        int row = submitMapper.updateById(submit);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (submitMapper.selectById(id) == null) {
            throw new BusinessException("作业提交不存在");
        }
        int row = submitMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkSubmit(HomeworkSubmit submit) {
        if (submit == null) {
            throw new BusinessException("作业提交信息不能为空");
        }
        if (submit.getHomeworkId() == null || homeworkMapper.selectById(submit.getHomeworkId()) == null) {
            throw new BusinessException("作业不存在");
        }
        if (submit.getStudentId() == null || userMapper.selectById(submit.getStudentId()) == null) {
            throw new BusinessException("学生用户不存在");
        }
        if (submit.getGraderId() != null && userMapper.selectById(submit.getGraderId()) == null) {
            throw new BusinessException("批改教师不存在");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
