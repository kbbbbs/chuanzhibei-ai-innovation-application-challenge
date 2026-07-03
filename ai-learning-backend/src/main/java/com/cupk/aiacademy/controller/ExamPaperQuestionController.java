package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
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

@RestController
@RequestMapping("/exam-paper-questions")
public class ExamPaperQuestionController {
    @Autowired
    private ExamPaperQuestionMapper paperQuestionMapper;

    @Autowired
    private ExamPaperMapper paperMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer paperId,
                        @RequestParam(required = false) Integer questionId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExamPaperQuestion> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<ExamPaperQuestion> queryWrapper = new QueryWrapper<>();
        if (paperId != null && paperId > 0) {
            queryWrapper.eq("paper_id", paperId);
        }
        if (questionId != null && questionId > 0) {
            queryWrapper.eq("question_id", questionId);
        }
        queryWrapper.orderByAsc("paper_id").orderByAsc("sort_order").orderByAsc("id");
        paperQuestionMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        ExamPaperQuestion item = paperQuestionMapper.selectById(id);
        return item == null ? Result.error("404", "试卷题目不存在") : Result.success(item);
    }

    @PostMapping
    public Result add(@RequestBody ExamPaperQuestion item) {
        Result checkResult = checkItem(item);
        if (checkResult != null) {
            return checkResult;
        }
        fixSortOrder(item);
        int row = paperQuestionMapper.insert(item);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ExamPaperQuestion item) {
        if (paperQuestionMapper.selectById(id) == null) {
            return Result.error("404", "试卷题目不存在");
        }
        Result checkResult = checkItem(item);
        if (checkResult != null) {
            return checkResult;
        }
        item.setId(id);
        fixSortOrder(item);
        int row = paperQuestionMapper.updateById(item);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        if (paperQuestionMapper.selectById(id) == null) {
            return Result.error("404", "试卷题目不存在");
        }
        int row = paperQuestionMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkItem(ExamPaperQuestion item) {
        if (item == null) {
            return Result.error("400", "试卷题目信息不能为空");
        }
        if (item.getPaperId() == null || item.getPaperId() <= 0) {
            return Result.error("400", "试卷不能为空");
        }
        if (item.getQuestionId() == null || item.getQuestionId() <= 0) {
            return Result.error("400", "题目不能为空");
        }
        if (paperMapper.selectById(item.getPaperId()) == null) {
            return Result.error("404", "试卷不存在");
        }
        if (questionMapper.selectById(item.getQuestionId()) == null) {
            return Result.error("404", "题目不存在");
        }
        return null;
    }

    private void fixSortOrder(ExamPaperQuestion item) {
        if (item.getSortOrder() == null || item.getSortOrder() <= 0) {
            item.setSortOrder(null);
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
