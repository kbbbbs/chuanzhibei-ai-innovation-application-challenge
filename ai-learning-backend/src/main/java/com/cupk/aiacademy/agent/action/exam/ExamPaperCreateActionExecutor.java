package com.cupk.aiacademy.agent.action.exam;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamPaper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class ExamPaperCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.paper.create";
    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_ARCHIVED = 2;

    private final ExamPaperMapper examPaperMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public ExamPaperCreateActionExecutor(ExamPaperMapper examPaperMapper,
                                         CourseMapper courseMapper,
                                         UserMapper userMapper) {
        this.examPaperMapper = examPaperMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建试卷";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer courseId = requiredInteger(parameters, "courseId", "course_id");
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        Integer teacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = isAdmin(user) && course.getTeacherId() != null ? course.getTeacherId() : user.getUserId();
        }
        validatePermission(user, course, teacherId);
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }

        ExamPaper paper = new ExamPaper();
        paper.setCourseId(courseId);
        paper.setTeacherId(teacherId);
        paper.setTitle(requiredString(parameters, 100, "title", "name", "paperTitle", "paper_title"));
        paper.setDescription(optionalString(parameters, 255, "description", "desc"));
        paper.setTotalScore(optionalScore(parameters));
        paper.setDurationMinutes(optionalDurationMinutes(parameters));
        paper.setStatus(optionalStatus(parameters));
        LocalDateTime now = LocalDateTime.now();
        paper.setCreateTime(now);
        paper.setUpdateTime(now);

        int row = examPaperMapper.insert(paper);
        if (row <= 0) {
            throw new BusinessException("创建试卷失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paperId", paper.getId());
        data.put("courseId", paper.getCourseId());
        data.put("teacherId", paper.getTeacherId());
        data.put("title", paper.getTitle());
        data.put("description", paper.getDescription());
        data.put("totalScore", paper.getTotalScore());
        data.put("durationMinutes", paper.getDurationMinutes());
        data.put("status", paper.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试卷已创建",
                data,
                LocalDateTime.now()
        );
    }

    private void validatePermission(AuthUtils.LoginUser user, Course course, Integer teacherId) {
        if (!isAdmin(user)) {
            if (!teacherId.equals(user.getUserId())) {
                throw new BusinessException("只能以当前登录教师身份创建试卷");
            }
            if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
                throw new BusinessException("只能给当前登录教师负责课程创建试卷");
            }
        }
        if (course.getTeacherId() != null && !course.getTeacherId().equals(teacherId)) {
            throw new BusinessException("试卷教师必须是课程负责教师");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以创建试卷");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private Integer optionalInteger(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
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

    private String requiredString(Map<String, Object> parameters, int maxLength, String... keys) {
        String value = optionalString(parameters, maxLength, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private String optionalString(Map<String, Object> parameters, int maxLength, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.length() > maxLength) {
            throw new BusinessException(keys[0] + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    private BigDecimal optionalScore(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "totalScore", "total_score", "fullScore", "full_score", "score");
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.valueOf(100);
        }
        try {
            BigDecimal score = new BigDecimal(String.valueOf(value).trim());
            if (score.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("totalScore 必须大于 0");
            }
            return score;
        } catch (NumberFormatException e) {
            throw new BusinessException("totalScore 必须是数字");
        }
    }

    private Integer optionalDurationMinutes(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "durationMinutes", "duration_minutes", "duration");
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
            if (parsed < 0) {
                throw new BusinessException("durationMinutes 不能小于 0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException("durationMinutes 必须是整数");
        }
    }

    private Integer optionalStatus(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "status");
        if (value == null || String.valueOf(value).isBlank()) {
            return STATUS_DRAFT;
        }
        if (value instanceof Number number) {
            return validateStatus(number.intValue());
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "draft", "草稿", "0" -> STATUS_DRAFT;
            case "published", "publish", "active", "发布", "已发布", "1" -> STATUS_PUBLISHED;
            case "archived", "archive", "归档", "2" -> STATUS_ARCHIVED;
            default -> throw new BusinessException("status 必须是草稿、已发布、归档或状态码");
        };
    }

    private Integer validateStatus(Integer status) {
        if (status == STATUS_DRAFT || status == STATUS_PUBLISHED || status == STATUS_ARCHIVED) {
            return status;
        }
        throw new BusinessException("status 必须是 0、1 或 2");
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
