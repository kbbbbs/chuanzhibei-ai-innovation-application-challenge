package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Question;
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
@RequestMapping("/questions")
public class QuestionController {
    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer courseId,
                        @RequestParam(required = false) Integer teacherId,
                        @RequestParam(defaultValue = "") String questionType,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Question> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (courseId != null && courseId > 0) {
            queryWrapper.eq("course_id", courseId);
        }
        if (teacherId != null && teacherId > 0) {
            queryWrapper.eq("teacher_id", teacherId);
        }
        if (questionType != null && !questionType.trim().isEmpty()) {
            queryWrapper.eq("question_type", questionType.trim());
        }
        queryWrapper.orderByDesc("id");
        questionMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        Question question = questionMapper.selectById(id);
        return question == null ? Result.error("404", "题目不存在") : Result.success(question);
    }

    @PostMapping
    public Result add(@RequestBody Question question) {
        Result checkResult = checkQuestion(question);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        question.setQuestionType(question.getQuestionType().trim());
        question.setCreateTime(now);
        question.setUpdateTime(now);
        int row = questionMapper.insert(question);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody Question question) {
        if (questionMapper.selectById(id) == null) {
            return Result.error("404", "题目不存在");
        }
        Result checkResult = checkQuestion(question);
        if (checkResult != null) {
            return checkResult;
        }
        question.setId(id);
        question.setQuestionType(question.getQuestionType().trim());
        question.setUpdateTime(LocalDateTime.now());
        int row = questionMapper.updateById(question);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (questionMapper.selectById(id) == null) {
            return Result.error("404", "题目不存在");
        }
        int row = questionMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkQuestion(Question question) {
        if (question == null) {
            return Result.error("400", "题目信息不能为空");
        }
        if (question.getCourseId() == null || courseMapper.selectById(question.getCourseId()) == null) {
            return Result.error("404", "课程不存在");
        }
        if (question.getTeacherId() == null || userMapper.selectById(question.getTeacherId()) == null) {
            return Result.error("404", "教师用户不存在");
        }
        if (question.getQuestionType() == null || question.getQuestionType().trim().isEmpty()) {
            return Result.error("400", "题型不能为空");
        }
        if (question.getTitle() == null || question.getTitle().trim().isEmpty()) {
            return Result.error("400", "题干不能为空");
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
