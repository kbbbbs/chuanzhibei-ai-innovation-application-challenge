package com.cupk.aiacademy.agent.action.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.service.QuestionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExamPaperQuestionAddActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.paper.question.add";

    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final CourseMapper courseMapper;
    private final QuestionService questionService;

    public ExamPaperQuestionAddActionExecutor(ExamPaperMapper examPaperMapper,
                                              ExamPaperQuestionMapper examPaperQuestionMapper,
                                              CourseMapper courseMapper,
                                              QuestionService questionService) {
        this.examPaperMapper = examPaperMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.courseMapper = courseMapper;
        this.questionService = questionService;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "试卷添加题目";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer paperId = requiredInteger(parameters, "paperId", "paper_id", "id");
        Long questionId = requiredLong(parameters, "questionId", "question_id");
        ExamPaper paper = requireDraftPaper(paperId);
        Course course = requireCourse(paper.getCourseId());
        QuestionResponseDTO question = questionService.getById(questionId);
        validatePermissionAndOwnership(user, paper, course, question);

        List<ExamPaperQuestion> existingQuestions = listPaperQuestions(paper.getId());
        ensureQuestionNotExists(existingQuestions, questionId);
        Integer sortOrder = hasAnyKey(parameters, "sortOrder", "sort_order", "order")
                ? requiredInteger(parameters, "sortOrder", "sort_order", "order")
                : nextSortOrder(existingQuestions);
        ensureSortOrderNotExists(existingQuestions, sortOrder);
        BigDecimal score = hasAnyKey(parameters, "score", "questionScore", "question_score")
                ? requiredScore(parameters, "score", "questionScore", "question_score")
                : defaultQuestionScore(question);
        Integer requiredAnswer = optionalRequiredAnswer(parameters);

        ExamPaperQuestion paperQuestion = new ExamPaperQuestion();
        paperQuestion.setPaperId(paper.getId());
        paperQuestion.setQuestionId(questionId.intValue());
        paperQuestion.setScore(score);
        paperQuestion.setSortOrder(sortOrder);
        paperQuestion.setRequiredAnswer(requiredAnswer);
        int row = examPaperQuestionMapper.insert(paperQuestion);
        if (row <= 0) {
            throw new BusinessException("试卷添加题目失败");
        }

        BigDecimal oldTotalScore = defaultScore(paper.getTotalScore());
        BigDecimal newTotalScore = totalScore(existingQuestions).add(score);
        ExamPaper update = new ExamPaper();
        update.setId(paper.getId());
        update.setTotalScore(newTotalScore);
        update.setUpdateTime(LocalDateTime.now());
        int updateRow = examPaperMapper.updateById(update);
        if (updateRow <= 0) {
            throw new BusinessException("更新试卷总分失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paperId", paper.getId());
        data.put("paperQuestionId", paperQuestion.getId());
        data.put("questionId", questionId);
        data.put("courseId", paper.getCourseId());
        data.put("teacherId", paper.getTeacherId());
        data.put("score", score);
        data.put("sortOrder", sortOrder);
        data.put("requiredAnswer", requiredAnswer);
        data.put("oldTotalScore", oldTotalScore);
        data.put("newTotalScore", newTotalScore);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "题目已加入试卷",
                data,
                LocalDateTime.now()
        );
    }

    private ExamPaper requireDraftPaper(Integer paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status == PaperStatus.PUBLISHED || status == PaperStatus.ARCHIVED) {
            throw new BusinessException("已发布或已归档的试卷不允许添加题目");
        }
        return paper;
    }

    private Course requireCourse(Integer courseId) {
        if (courseId == null || courseId <= 0) {
            throw new BusinessException("课程不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private void validatePermissionAndOwnership(AuthUtils.LoginUser user,
                                                ExamPaper paper,
                                                Course course,
                                                QuestionResponseDTO question) {
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        Integer questionCourseId = toInteger(question.getCourseId(), "题目课程ID");
        Integer questionTeacherId = toInteger(question.getTeacherId(), "题目教师ID");
        if (!paper.getCourseId().equals(questionCourseId)) {
            throw new BusinessException("题目不属于当前试卷课程");
        }
        if (!paper.getTeacherId().equals(questionTeacherId)) {
            throw new BusinessException("题目不属于当前试卷教师");
        }
        if (!isAdmin(user)) {
            if (paper.getTeacherId() == null || !paper.getTeacherId().equals(user.getUserId())) {
                throw new BusinessException("只能给当前登录教师负责的试卷添加题目");
            }
            if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
                throw new BusinessException("只能给当前登录教师负责课程下的试卷添加题目");
            }
        }
    }

    private List<ExamPaperQuestion> listPaperQuestions(Integer paperId) {
        QueryWrapper<ExamPaperQuestion> query = new QueryWrapper<>();
        query.eq("paper_id", paperId);
        return examPaperQuestionMapper.selectList(query);
    }

    private void ensureQuestionNotExists(List<ExamPaperQuestion> existingQuestions, Long questionId) {
        int parsedQuestionId = questionId.intValue();
        boolean exists = existingQuestions.stream()
                .anyMatch(item -> item.getQuestionId() != null && item.getQuestionId() == parsedQuestionId);
        if (exists) {
            throw new BusinessException("试卷中已存在该题目");
        }
    }

    private void ensureSortOrderNotExists(List<ExamPaperQuestion> existingQuestions, Integer sortOrder) {
        boolean exists = existingQuestions.stream()
                .anyMatch(item -> item.getSortOrder() != null && item.getSortOrder().equals(sortOrder));
        if (exists) {
            throw new BusinessException("试卷中题目序号不能重复");
        }
    }

    private Integer nextSortOrder(List<ExamPaperQuestion> existingQuestions) {
        return existingQuestions.stream()
                .map(ExamPaperQuestion::getSortOrder)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private BigDecimal totalScore(List<ExamPaperQuestion> existingQuestions) {
        return existingQuestions.stream()
                .map(ExamPaperQuestion::getScore)
                .map(this::defaultScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal defaultQuestionScore(QuestionResponseDTO question) {
        BigDecimal score = defaultScore(question.getScore());
        if (score.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("题目分值必须大于 0");
        }
        return score;
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }

    private Integer optionalRequiredAnswer(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "requiredAnswer", "required_answer", "required");
        if (value == null || String.valueOf(value).isBlank()) {
            return 1;
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed == 0 || parsed == 1) {
                return parsed;
            }
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return switch (normalized) {
            case "true", "yes", "y", "1", "是", "需要", "必答" -> 1;
            case "false", "no", "n", "0", "否", "不需要", "选答" -> 0;
            default -> throw new BusinessException("requiredAnswer 必须是布尔值或 0/1");
        };
    }

    private BigDecimal requiredScore(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        try {
            BigDecimal score = new BigDecimal(String.valueOf(value).trim());
            if (score.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(keys[0] + " 必须大于 0");
            }
            return score;
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是数字");
        }
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
            if (parsed <= 0) {
                throw new BusinessException(keys[0] + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private Long requiredLong(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        try {
            long parsed = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value).trim());
            if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
                throw new BusinessException(keys[0] + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private Integer toInteger(Long value, String fieldName) {
        if (value == null || value <= 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(fieldName + "不合法");
        }
        return value.intValue();
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以给试卷添加题目");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Object firstValue(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return parameters.get(key);
            }
        }
        return null;
    }

    private boolean hasAnyKey(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
