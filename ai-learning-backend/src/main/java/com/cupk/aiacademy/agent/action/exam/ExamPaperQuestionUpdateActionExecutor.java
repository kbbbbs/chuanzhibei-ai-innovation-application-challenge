package com.cupk.aiacademy.agent.action.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamAnswer;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExamPaperQuestionUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.paper.question.update";

    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final CourseMapper courseMapper;
    private final ExamAnswerMapper examAnswerMapper;

    public ExamPaperQuestionUpdateActionExecutor(ExamPaperMapper examPaperMapper,
                                                 ExamPaperQuestionMapper examPaperQuestionMapper,
                                                 CourseMapper courseMapper,
                                                 ExamAnswerMapper examAnswerMapper) {
        this.examPaperMapper = examPaperMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.courseMapper = courseMapper;
        this.examAnswerMapper = examAnswerMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "调整试卷题目";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer paperId = requiredInteger(parameters, "paperId", "paper_id");
        requireAtLeastOneUpdateField(parameters);
        ExamPaper paper = requireDraftPaper(paperId);
        Course course = requireCourse(paper.getCourseId());
        validatePermission(user, paper, course);

        ExamPaperQuestion relation = resolvePaperQuestion(parameters, paper.getId());
        if (!paper.getId().equals(relation.getPaperId())) {
            throw new BusinessException("试卷题目不属于当前试卷");
        }
        Long affectedAnswerCount = examAnswerMapper.selectCount(
                new QueryWrapper<ExamAnswer>().eq("paper_question_id", relation.getId())
        );
        if (affectedAnswerCount > 0) {
            throw new BusinessException("该试卷题目已被答卷使用，不能调整");
        }

        List<ExamPaperQuestion> existingQuestions = listPaperQuestions(paper.getId());
        BigDecimal oldScore = defaultScore(relation.getScore());
        BigDecimal newScore = hasAnyKey(parameters, "score", "questionScore", "question_score")
                ? requiredScore(parameters, "score", "questionScore", "question_score")
                : oldScore;
        Integer oldSortOrder = relation.getSortOrder();
        Integer newSortOrder = hasAnyKey(parameters, "sortOrder", "sort_order", "order")
                ? requiredInteger(parameters, "sortOrder", "sort_order", "order")
                : oldSortOrder;
        ensureSortOrderNotExists(existingQuestions, relation.getId(), newSortOrder);
        Integer oldRequiredAnswer = defaultRequiredAnswer(relation.getRequiredAnswer());
        Integer newRequiredAnswer = hasAnyKey(parameters, "requiredAnswer", "required_answer", "required")
                ? requiredAnswer(parameters)
                : oldRequiredAnswer;

        ExamPaperQuestion updateRelation = new ExamPaperQuestion();
        updateRelation.setId(relation.getId());
        updateRelation.setPaperId(relation.getPaperId());
        updateRelation.setQuestionId(relation.getQuestionId());
        updateRelation.setScore(newScore);
        updateRelation.setSortOrder(newSortOrder);
        updateRelation.setRequiredAnswer(newRequiredAnswer);
        int row = examPaperQuestionMapper.updateById(updateRelation);
        if (row <= 0) {
            throw new BusinessException("调整试卷题目失败");
        }

        BigDecimal oldTotalScore = defaultScore(paper.getTotalScore());
        BigDecimal newTotalScore = existingQuestions.stream()
                .map(item -> relation.getId().equals(item.getId()) ? newScore : defaultScore(item.getScore()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ExamPaper updatePaper = new ExamPaper();
        updatePaper.setId(paper.getId());
        updatePaper.setTotalScore(newTotalScore);
        updatePaper.setUpdateTime(LocalDateTime.now());
        int updatePaperRow = examPaperMapper.updateById(updatePaper);
        if (updatePaperRow <= 0) {
            throw new BusinessException("更新试卷总分失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paperId", paper.getId());
        data.put("paperQuestionId", relation.getId());
        data.put("questionId", relation.getQuestionId());
        data.put("courseId", paper.getCourseId());
        data.put("teacherId", paper.getTeacherId());
        data.put("oldScore", oldScore);
        data.put("newScore", newScore);
        data.put("oldSortOrder", oldSortOrder);
        data.put("newSortOrder", newSortOrder);
        data.put("oldRequiredAnswer", oldRequiredAnswer);
        data.put("newRequiredAnswer", newRequiredAnswer);
        data.put("oldTotalScore", oldTotalScore);
        data.put("newTotalScore", newTotalScore);
        data.put("affectedAnswerCount", affectedAnswerCount);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试卷题目已调整",
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
            throw new BusinessException("已发布或已归档的试卷不允许调整题目");
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

    private void validatePermission(AuthUtils.LoginUser user, ExamPaper paper, Course course) {
        if (isAdmin(user)) {
            return;
        }
        if (paper.getTeacherId() == null || !paper.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能调整当前登录教师负责的试卷题目");
        }
        if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能调整当前登录教师负责课程下的试卷题目");
        }
    }

    private ExamPaperQuestion resolvePaperQuestion(Map<String, Object> parameters, Integer paperId) {
        if (hasAnyKey(parameters, "paperQuestionId", "paper_question_id", "relationId", "relation_id")) {
            Integer paperQuestionId = requiredInteger(parameters, "paperQuestionId", "paper_question_id",
                    "relationId", "relation_id");
            ExamPaperQuestion relation = examPaperQuestionMapper.selectById(paperQuestionId);
            if (relation == null) {
                throw new BusinessException("试卷题目不存在");
            }
            return relation;
        }
        if (hasAnyKey(parameters, "questionId", "question_id")) {
            Integer questionId = requiredInteger(parameters, "questionId", "question_id");
            QueryWrapper<ExamPaperQuestion> query = new QueryWrapper<>();
            query.eq("paper_id", paperId).eq("question_id", questionId);
            ExamPaperQuestion relation = examPaperQuestionMapper.selectOne(query);
            if (relation == null) {
                throw new BusinessException("试卷中不存在该题目");
            }
            return relation;
        }
        throw new BusinessException("paperQuestionId 或 questionId 不能为空");
    }

    private List<ExamPaperQuestion> listPaperQuestions(Integer paperId) {
        QueryWrapper<ExamPaperQuestion> query = new QueryWrapper<>();
        query.eq("paper_id", paperId);
        return examPaperQuestionMapper.selectList(query);
    }

    private void requireAtLeastOneUpdateField(Map<String, Object> parameters) {
        if (!hasAnyKey(parameters, "score", "questionScore", "question_score",
                "sortOrder", "sort_order", "order", "requiredAnswer", "required_answer", "required")) {
            throw new BusinessException("至少需要提供一个可修改的试卷题目字段");
        }
    }

    private void ensureSortOrderNotExists(List<ExamPaperQuestion> existingQuestions,
                                          Integer currentRelationId,
                                          Integer sortOrder) {
        if (sortOrder == null) {
            return;
        }
        boolean exists = existingQuestions.stream()
                .anyMatch(item -> item.getSortOrder() != null
                        && item.getSortOrder().equals(sortOrder)
                        && !item.getId().equals(currentRelationId));
        if (exists) {
            throw new BusinessException("试卷中题目序号不能重复");
        }
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

    private Integer requiredAnswer(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "requiredAnswer", "required_answer", "required");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException("requiredAnswer 不能为空");
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

    private Integer defaultRequiredAnswer(Integer value) {
        return value == null ? 1 : value;
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以调整试卷题目");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
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
