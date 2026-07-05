package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

// Legacy CRUD controller disabled; use /api/papers instead.
public class ExamPaperController {
    @Autowired
    private ExamPaperMapper paperMapper;

    @Autowired
    private ExamPaperQuestionMapper paperQuestionMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/exam-papers")
    public Result pages(@RequestParam(required = false) Integer courseId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExamPaper> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ExamPaper> queryWrapper = new QueryWrapper<>();
        if (courseId != null && courseId > 0) {
            queryWrapper.eq("course_id", courseId);
        }
        if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        queryWrapper.orderByDesc("id");
        paperMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/exam-papers/{id}")
    public Result selectById(@PathVariable Integer id) {
        ExamPaper paper = paperMapper.selectById(id);
        return paper == null ? Result.error("404", "试卷不存在") : Result.success(paper);
    }

    @GetMapping("/exam-papers/{paperId}/questions")
    public Result listQuestions(@PathVariable Integer paperId) {
        if (paperMapper.selectById(paperId) == null) {
            return Result.error("404", "试卷不存在");
        }
        QueryWrapper<ExamPaperQuestion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("paper_id", paperId).orderByAsc("sort_order").orderByAsc("id");
        return Result.success(paperQuestionMapper.selectList(queryWrapper));
    }

    @PostMapping("/exam-papers")
    public Result add(@RequestBody ExamPaper paper) {
        Result checkResult = checkPaper(paper);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        paper.setTitle(paper.getTitle().trim());
        paper.setCreateTime(now);
        paper.setUpdateTime(now);
        int row = paperMapper.insert(paper);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/exam-papers/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ExamPaper paper) {
        if (paperMapper.selectById(id) == null) {
            return Result.error("404", "试卷不存在");
        }
        Result checkResult = checkPaper(paper);
        if (checkResult != null) {
            return checkResult;
        }
        paper.setId(id);
        paper.setTitle(paper.getTitle().trim());
        paper.setUpdateTime(LocalDateTime.now());
        int row = paperMapper.updateById(paper);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/exam-papers/{id}")
    public Result delete(@PathVariable Integer id) {
        if (paperMapper.selectById(id) == null) {
            return Result.error("404", "试卷不存在");
        }
        int row = paperMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkPaper(ExamPaper paper) {
        if (paper == null) {
            return Result.error("400", "试卷信息不能为空");
        }
        if (paper.getCourseId() == null || courseMapper.selectById(paper.getCourseId()) == null) {
            return Result.error("404", "课程不存在");
        }
        if (paper.getTeacherId() == null || userMapper.selectById(paper.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (paper.getTitle() == null || paper.getTitle().trim().isEmpty()) {
            return Result.error("400", "试卷名称不能为空");
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
