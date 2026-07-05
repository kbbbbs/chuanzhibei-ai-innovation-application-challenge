package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ExamRecord;
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

// Legacy CRUD controller disabled; use /api/submissions instead.
@RequestMapping("/exam-records")
public class ExamRecordController {
    @Autowired
    private ExamRecordMapper recordMapper;

    @Autowired
    private ExamSessionMapper sessionMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer examSessionId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExamRecord> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ExamRecord> queryWrapper = new QueryWrapper<>();
        if (examSessionId != null && examSessionId > 0) {
            queryWrapper.eq("exam_session_id", examSessionId);
        }
        if (studentId != null && studentId > 0) {
            queryWrapper.eq("student_id", studentId);
        }
        queryWrapper.orderByDesc("id");
        recordMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        ExamRecord record = recordMapper.selectById(id);
        return record == null ? Result.error("404", "考试记录不存在") : Result.success(record);
    }

    @PostMapping
    public Result add(@RequestBody ExamRecord record) {
        Result checkResult = checkRecord(record);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        int row = recordMapper.insert(record);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ExamRecord record) {
        if (recordMapper.selectById(id) == null) {
            return Result.error("404", "考试记录不存在");
        }
        Result checkResult = checkRecord(record);
        if (checkResult != null) {
            return checkResult;
        }
        record.setId(id);
        record.setUpdateTime(LocalDateTime.now());
        int row = recordMapper.updateById(record);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (recordMapper.selectById(id) == null) {
            return Result.error("404", "考试记录不存在");
        }
        int row = recordMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkRecord(ExamRecord record) {
        if (record == null) {
            return Result.error("400", "考试记录信息不能为空");
        }
        if (record.getExamSessionId() == null || record.getExamSessionId() <= 0) {
            return Result.error("400", "考试安排不能为空");
        }
        if (record.getStudentId() == null || record.getStudentId() <= 0) {
            return Result.error("400", "学生用户不能为空");
        }
        if (sessionMapper.selectById(record.getExamSessionId()) == null) {
            return Result.error("404", "考试安排不存在");
        }
        if (userMapper.selectById(record.getStudentId()) == null) {
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
