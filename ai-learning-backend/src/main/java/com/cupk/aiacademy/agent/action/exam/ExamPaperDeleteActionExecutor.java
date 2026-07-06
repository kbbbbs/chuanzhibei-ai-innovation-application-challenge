package com.cupk.aiacademy.agent.action.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.exam.ExamPaperDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.pojo.ExamSession;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExamPaperDeleteActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.paper.delete";
    private static final int STATUS_ARCHIVED = 2;

    private final ExamPaperMapper examPaperMapper;
    private final CourseMapper courseMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final ExamSessionMapper examSessionMapper;

    public ExamPaperDeleteActionExecutor(ExamPaperMapper examPaperMapper,
                                         CourseMapper courseMapper,
                                         ExamPaperQuestionMapper examPaperQuestionMapper,
                                         ExamSessionMapper examSessionMapper) {
        this.examPaperMapper = examPaperMapper;
        this.courseMapper = courseMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.examSessionMapper = examSessionMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除试卷";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ExamPaperDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Integer paperId = requiredInteger(context.parameters(), "paperId", "paper_id", "id");
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (STATUS_ARCHIVED == defaultStatus(paper.getStatus())) {
            throw new BusinessException("试卷已归档，无需重复删除");
        }

        Course course = courseMapper.selectById(paper.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        validatePermission(user, paper, course);

        Long affectedPaperQuestionCount = examPaperQuestionMapper.selectCount(
                new QueryWrapper<ExamPaperQuestion>().eq("paper_id", paper.getId())
        );
        Long affectedExamSessionCount = examSessionMapper.selectCount(
                new QueryWrapper<ExamSession>().eq("paper_id", paper.getId())
        );

        ExamPaper update = new ExamPaper();
        update.setId(paper.getId());
        update.setStatus(STATUS_ARCHIVED);
        update.setUpdateTime(LocalDateTime.now());
        int row = examPaperMapper.updateById(update);
        if (row <= 0) {
            throw new BusinessException("删除试卷失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paperId", paper.getId());
        data.put("courseId", paper.getCourseId());
        data.put("teacherId", paper.getTeacherId());
        data.put("title", paper.getTitle());
        data.put("oldStatus", defaultStatus(paper.getStatus()));
        data.put("newStatus", STATUS_ARCHIVED);
        data.put("softDeleted", true);
        data.put("deleteMode", "soft_delete");
        data.put("affectedPaperQuestionCount", affectedPaperQuestionCount);
        data.put("affectedExamSessionCount", affectedExamSessionCount);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试卷已删除",
                data,
                LocalDateTime.now()
        );
    }

    private void validatePermission(AuthUtils.LoginUser user, ExamPaper paper, Course course) {
        if (isAdmin(user)) {
            return;
        }
        if (paper.getTeacherId() == null || !paper.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能删除当前登录教师负责的试卷");
        }
        if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能删除当前登录教师负责课程下的试卷");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以删除试卷");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private int defaultStatus(Integer status) {
        return status == null ? 0 : status;
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
}
