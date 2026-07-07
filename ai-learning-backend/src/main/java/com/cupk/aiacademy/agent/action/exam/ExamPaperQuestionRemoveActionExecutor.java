package com.cupk.aiacademy.agent.action.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.exam.ExamPaperQuestionRemovePlanTool;
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
public class ExamPaperQuestionRemoveActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.paper.question.remove";

    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final CourseMapper courseMapper;
    private final ExamAnswerMapper examAnswerMapper;

    public ExamPaperQuestionRemoveActionExecutor(ExamPaperMapper examPaperMapper,
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
        return "试卷移除题目";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ExamPaperQuestionRemovePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer paperId = requiredInteger(parameters, "paperId", "paper_id");
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
            throw new BusinessException("该试卷题目已被答卷使用，不能删除");
        }

        List<ExamPaperQuestion> existingQuestions = listPaperQuestions(paper.getId());
        BigDecimal oldTotalScore = defaultScore(paper.getTotalScore());
        BigDecimal newTotalScore = existingQuestions.stream()
                .filter(item -> !relation.getId().equals(item.getId()))
                .map(ExamPaperQuestion::getScore)
                .map(this::defaultScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int row = examPaperQuestionMapper.deleteById(relation.getId());
        if (row <= 0) {
            throw new BusinessException("试卷移除题目失败");
        }
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
        data.put("paperQuestionId", relation.getId());
        data.put("questionId", relation.getQuestionId());
        data.put("courseId", paper.getCourseId());
        data.put("teacherId", paper.getTeacherId());
        data.put("removedScore", relation.getScore());
        data.put("removedSortOrder", relation.getSortOrder());
        data.put("oldTotalScore", oldTotalScore);
        data.put("newTotalScore", newTotalScore);
        data.put("affectedAnswerCount", affectedAnswerCount);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试卷题目已移除",
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
            throw new BusinessException("已发布或已归档的试卷不允许删除题目");
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
            throw new BusinessException("只能从当前登录教师负责的试卷移除题目");
        }
        if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能从当前登录教师负责课程下的试卷移除题目");
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

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以从试卷移除题目");
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
