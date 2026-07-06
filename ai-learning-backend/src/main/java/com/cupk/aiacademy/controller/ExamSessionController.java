package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ExamSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Deprecated
@RequestMapping("/exam-sessions")
public class ExamSessionController {
    @Autowired
    private ExamSessionMapper sessionMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private ExamPaperMapper paperMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer paperId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExamSession> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ExamSession> queryWrapper = new QueryWrapper<>();
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        if (paperId != null && paperId > 0) {
            queryWrapper.eq("paper_id", paperId);
        }
        if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        queryWrapper.orderByDesc("id");
        sessionMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        ExamSession session = sessionMapper.selectById(id);
        return session == null ? Result.error("404", "考试安排不存在") : Result.success(session);
    }

    @PostMapping
    public Result add(@RequestBody ExamSession session) {
        Result checkResult = checkSession(session);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        session.setTitle(session.getTitle().trim());
        session.setCreateTime(now);
        session.setUpdateTime(now);
        int row = sessionMapper.insert(session);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ExamSession session) {
        if (sessionMapper.selectById(id) == null) {
            return Result.error("404", "考试安排不存在");
        }
        Result checkResult = checkSession(session);
        if (checkResult != null) {
            return checkResult;
        }
        session.setId(id);
        session.setTitle(session.getTitle().trim());
        session.setUpdateTime(LocalDateTime.now());
        int row = sessionMapper.updateById(session);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (sessionMapper.selectById(id) == null) {
            return Result.error("404", "考试安排不存在");
        }
        int row = sessionMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkSession(ExamSession session) {
        if (session == null) {
            return Result.error("400", "考试安排信息不能为空");
        }
        if (session.getClassId() == null || classMapper.selectById(session.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
        }
        if (session.getPaperId() == null || paperMapper.selectById(session.getPaperId()) == null) {
            return Result.error("404", "试卷不存在");
        }
        if (session.getTeacherId() == null || userMapper.selectById(session.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (session.getTitle() == null || session.getTitle().trim().isEmpty()) {
            return Result.error("400", "考试名称不能为空");
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
