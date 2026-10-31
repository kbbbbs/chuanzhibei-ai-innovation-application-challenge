package com.cupk.aiacademy.agent.action.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.exam.QuestionDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamAnswer;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.service.QuestionService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class QuestionDeleteActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "question.delete";

    private final QuestionService questionService;
    private final CourseMapper courseMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final ExamAnswerMapper examAnswerMapper;

    public QuestionDeleteActionExecutor(QuestionService questionService,
                                        CourseMapper courseMapper,
                                        ExamPaperQuestionMapper examPaperQuestionMapper,
                                        ExamAnswerMapper examAnswerMapper) {
        this.questionService = questionService;
        this.courseMapper = courseMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.examAnswerMapper = examAnswerMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除试题";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return QuestionDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Long questionId = requiredLong(context.parameters(), "questionId", "question_id", "id");
        QuestionResponseDTO question = questionService.getById(questionId);
        Course course = requireCourse(question.getCourseId());
        validatePermission(user, question, course);

        Long affectedPaperQuestionCount = examPaperQuestionMapper.selectCount(
                new QueryWrapper<ExamPaperQuestion>().eq("question_id", questionId)
        );
        Long affectedAnswerCount = examAnswerMapper.selectCount(
                new QueryWrapper<ExamAnswer>().eq("question_id", questionId)
        );

        questionService.delete(questionId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", question.getId());
        data.put("courseId", question.getCourseId());
        data.put("teacherId", question.getTeacherId());
        data.put("type", enumName(question.getType()));
        data.put("difficulty", enumName(question.getDifficulty()));
        data.put("status", enumName(question.getStatus()));
        data.put("content", question.getContent());
        data.put("affectedPaperQuestionCount", affectedPaperQuestionCount);
        data.put("affectedAnswerCount", affectedAnswerCount);
        data.put("deleteMode", "physical_delete");
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试题已删除",
                data,
                LocalDateTime.now()
        );
    }

    private Course requireCourse(Long courseId) {
        Integer parsedCourseId = toInteger(courseId, "课程ID");
        Course course = courseMapper.selectById(parsedCourseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private void validatePermission(AuthUtils.LoginUser user, QuestionResponseDTO question, Course course) {
        if (isAdmin(user)) {
            return;
        }
        Integer questionTeacherId = toInteger(question.getTeacherId(), "教师ID");
        if (!questionTeacherId.equals(user.getUserId())) {
            throw new BusinessException("只能删除当前登录教师负责的试题");
        }
        if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能删除当前登录教师负责课程下的试题");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以删除试题");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
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
            if (parsed <= 0) {
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

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
