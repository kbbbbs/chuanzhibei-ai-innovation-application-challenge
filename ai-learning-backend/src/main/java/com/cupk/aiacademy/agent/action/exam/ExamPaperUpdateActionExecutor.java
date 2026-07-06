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
public class ExamPaperUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.paper.update";
    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_ARCHIVED = 2;

    private final ExamPaperMapper examPaperMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public ExamPaperUpdateActionExecutor(ExamPaperMapper examPaperMapper,
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
        return "修改试卷";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer paperId = requiredInteger(parameters, "paperId", "paper_id", "id");
        requireAtLeastOneUpdateField(parameters);

        ExamPaper oldPaper = examPaperMapper.selectById(paperId);
        if (oldPaper == null) {
            throw new BusinessException("试卷不存在");
        }

        Integer targetCourseId = hasAnyKey(parameters, "courseId", "course_id")
                ? requiredInteger(parameters, "courseId", "course_id")
                : oldPaper.getCourseId();
        Course targetCourse = requireCourse(targetCourseId);
        Integer targetTeacherId = hasAnyKey(parameters, "teacherId", "teacher_id")
                ? requiredInteger(parameters, "teacherId", "teacher_id")
                : oldPaper.getTeacherId();
        validatePermission(user, oldPaper, targetCourse, targetTeacherId);
        if (userMapper.selectById(targetTeacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }

        ExamPaper update = new ExamPaper();
        update.setId(oldPaper.getId());
        update.setCourseId(targetCourseId);
        update.setTeacherId(targetTeacherId);
        update.setTitle(hasAnyKey(parameters, "title", "name", "paperTitle", "paper_title")
                ? requiredString(parameters, 100, "title", "name", "paperTitle", "paper_title")
                : oldPaper.getTitle());
        update.setDescription(hasAnyKey(parameters, "description", "desc")
                ? optionalString(parameters, 255, "description", "desc")
                : oldPaper.getDescription());
        update.setTotalScore(hasAnyKey(parameters, "totalScore", "total_score", "fullScore", "full_score", "score")
                ? requiredScore(parameters)
                : oldPaper.getTotalScore());
        update.setDurationMinutes(hasAnyKey(parameters, "durationMinutes", "duration_minutes", "duration")
                ? requiredDurationMinutes(parameters)
                : oldPaper.getDurationMinutes());
        update.setStatus(hasAnyKey(parameters, "status") ? requiredStatus(parameters) : oldPaper.getStatus());
        update.setCreateTime(oldPaper.getCreateTime());
        update.setUpdateTime(LocalDateTime.now());

        int row = examPaperMapper.updateById(update);
        if (row <= 0) {
            throw new BusinessException("修改试卷失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paperId", update.getId());
        data.put("oldCourseId", oldPaper.getCourseId());
        data.put("newCourseId", update.getCourseId());
        data.put("oldTeacherId", oldPaper.getTeacherId());
        data.put("newTeacherId", update.getTeacherId());
        data.put("oldTitle", oldPaper.getTitle());
        data.put("newTitle", update.getTitle());
        data.put("oldTotalScore", oldPaper.getTotalScore());
        data.put("newTotalScore", update.getTotalScore());
        data.put("oldDurationMinutes", oldPaper.getDurationMinutes());
        data.put("newDurationMinutes", update.getDurationMinutes());
        data.put("oldStatus", oldPaper.getStatus());
        data.put("newStatus", update.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试卷已修改",
                data,
                LocalDateTime.now()
        );
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

    private void validatePermission(AuthUtils.LoginUser user,
                                    ExamPaper oldPaper,
                                    Course targetCourse,
                                    Integer targetTeacherId) {
        if (!isAdmin(user)) {
            if (oldPaper.getTeacherId() == null || !oldPaper.getTeacherId().equals(user.getUserId())) {
                throw new BusinessException("只能修改当前登录教师负责的试卷");
            }
            if (!targetTeacherId.equals(user.getUserId())) {
                throw new BusinessException("只能以当前登录教师身份修改试卷");
            }
            if (targetCourse.getTeacherId() == null || !targetCourse.getTeacherId().equals(user.getUserId())) {
                throw new BusinessException("只能把试卷归属到当前登录教师负责的课程");
            }
        }
        if (targetCourse.getTeacherId() != null && !targetCourse.getTeacherId().equals(targetTeacherId)) {
            throw new BusinessException("试卷教师必须是课程负责教师");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改试卷");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private void requireAtLeastOneUpdateField(Map<String, Object> parameters) {
        if (!hasAnyKey(parameters, "courseId", "course_id", "teacherId", "teacher_id",
                "title", "name", "paperTitle", "paper_title", "description", "desc",
                "totalScore", "total_score", "fullScore", "full_score", "score",
                "durationMinutes", "duration_minutes", "duration", "status")) {
            throw new BusinessException("至少需要提供一个可修改的试卷字段");
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

    private BigDecimal requiredScore(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "totalScore", "total_score", "fullScore", "full_score", "score");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException("totalScore 不能为空");
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

    private Integer requiredDurationMinutes(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "durationMinutes", "duration_minutes", "duration");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException("durationMinutes 不能为空");
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

    private Integer requiredStatus(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "status");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException("status 不能为空");
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
