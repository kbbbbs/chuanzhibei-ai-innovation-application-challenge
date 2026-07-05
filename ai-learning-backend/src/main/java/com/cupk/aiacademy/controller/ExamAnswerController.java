package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ExamAnswer;
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

// Legacy CRUD controller disabled; use /api/submissions instead.
@RequestMapping("/exam-answers")
public class ExamAnswerController {
    @Autowired
    private ExamAnswerMapper answerMapper;

    @Autowired
    private ExamRecordMapper recordMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer examRecordId,
                        @RequestParam(required = false) Integer questionId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExamAnswer> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ExamAnswer> queryWrapper = new QueryWrapper<>();
        if (examRecordId != null && examRecordId > 0) {
            queryWrapper.eq("exam_record_id", examRecordId);
        }
        if (questionId != null && questionId > 0) {
            queryWrapper.eq("question_id", questionId);
        }
        queryWrapper.orderByDesc("id");
        answerMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        ExamAnswer answer = answerMapper.selectById(id);
        return answer == null ? Result.error("404", "考试答题不存在") : Result.success(answer);
    }

    @PostMapping
    public Result add(@RequestBody ExamAnswer answer) {
        Result checkResult = checkAnswer(answer);
        if (checkResult != null) {
            return checkResult;
        }
        int row = answerMapper.insert(answer);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ExamAnswer answer) {
        if (answerMapper.selectById(id) == null) {
            return Result.error("404", "考试答题不存在");
        }
        Result checkResult = checkAnswer(answer);
        if (checkResult != null) {
            return checkResult;
        }
        answer.setId(id);
        int row = answerMapper.updateById(answer);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (answerMapper.selectById(id) == null) {
            return Result.error("404", "考试答题不存在");
        }
        int row = answerMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkAnswer(ExamAnswer answer) {
        if (answer == null) {
            return Result.error("400", "考试答题信息不能为空");
        }
        if (answer.getExamRecordId() == null || answer.getExamRecordId() <= 0) {
            return Result.error("400", "考试记录不能为空");
        }
        if (answer.getQuestionId() == null || answer.getQuestionId() <= 0) {
            return Result.error("400", "题目不能为空");
        }
        if (recordMapper.selectById(answer.getExamRecordId()) == null) {
            return Result.error("404", "考试记录不存在");
        }
        if (questionMapper.selectById(answer.getQuestionId()) == null) {
            return Result.error("404", "题目不存在");
        }
        if (answer.getGraderId() != null && userMapper.selectById(answer.getGraderId()) == null) {
            return Result.error("404", "批改教师不存在");
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
