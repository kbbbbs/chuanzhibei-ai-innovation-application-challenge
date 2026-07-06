package com.cupk.aiacademy.agent.action.grade;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordCreatePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.GradeRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class GradeRecordCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "grade.record.create";
    private static final Set<String> VALID_SOURCE_TYPES = Set.of("homework", "exam", "manual", "import");

    private final GradeRecordMapper gradeRecordMapper;
    private final UserMapper userMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;

    public GradeRecordCreateActionExecutor(GradeRecordMapper gradeRecordMapper,
                                           UserMapper userMapper,
                                           TeachingClassMapper teachingClassMapper,
                                           CourseMapper courseMapper) {
        this.gradeRecordMapper = gradeRecordMapper;
        this.userMapper = userMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "录入成绩记录";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return GradeRecordCreatePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer studentId = requiredInteger(parameters, "studentId", "student_id");
        Integer classId = requiredInteger(parameters, "classId", "class_id");
        Integer courseId = requiredInteger(parameters, "courseId", "course_id");
        Integer teacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = user.getUserId();
        }
        if (!teacherId.equals(user.getUserId()) && !isAdmin(user)) {
            throw new BusinessException("只能以当前登录教师身份录入成绩");
        }

        validateReferences(studentId, classId, courseId, teacherId);

        String gradeName = requiredString(parameters, 100, "gradeName", "grade_name", "name");
        String sourceType = optionalString(parameters, "sourceType", "source_type");
        if (sourceType == null) {
            sourceType = "manual";
        }
        sourceType = sourceType.trim();
        if (!VALID_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException("成绩来源不合法");
        }

        BigDecimal score = requiredDecimal(parameters, "score");
        BigDecimal fullScore = optionalDecimal(parameters, "fullScore", "full_score");
        if (fullScore == null) {
            fullScore = BigDecimal.valueOf(100);
        }
        requirePositive(fullScore, "fullScore");
        if (score.compareTo(fullScore) > 0) {
            throw new BusinessException("分数不能超过满分");
        }

        Integer sourceId = optionalInteger(parameters, "sourceId", "source_id");
        String comment = optionalString(parameters, "comment", "gradeComment", "grade_comment");
        if (comment != null && comment.length() > 255) {
            throw new BusinessException("comment 长度不能超过 255");
        }

        LocalDateTime now = LocalDateTime.now();
        GradeRecord grade = new GradeRecord();
        grade.setStudentId(studentId);
        grade.setClassId(classId);
        grade.setCourseId(courseId);
        grade.setTeacherId(teacherId);
        grade.setGradeName(gradeName);
        grade.setSourceType(sourceType);
        grade.setSourceId(sourceId);
        grade.setScore(score);
        grade.setFullScore(fullScore);
        grade.setComment(comment);
        grade.setCreateTime(now);
        grade.setUpdateTime(now);

        int row = gradeRecordMapper.insert(grade);
        if (row <= 0) {
            throw new BusinessException("录入成绩失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gradeRecordId", grade.getId());
        data.put("studentId", grade.getStudentId());
        data.put("classId", grade.getClassId());
        data.put("courseId", grade.getCourseId());
        data.put("teacherId", grade.getTeacherId());
        data.put("gradeName", grade.getGradeName());
        data.put("sourceType", grade.getSourceType());
        data.put("score", grade.getScore());
        data.put("fullScore", grade.getFullScore());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "成绩记录已录入",
                data,
                LocalDateTime.now()
        );
    }

    private void validateReferences(Integer studentId, Integer classId, Integer courseId, Integer teacherId) {
        if (userMapper.selectById(studentId) == null) {
            throw new BusinessException("学生用户不存在");
        }
        if (teachingClassMapper.selectById(classId) == null) {
            throw new BusinessException("教学班不存在");
        }
        if (courseMapper.selectById(courseId) == null) {
            throw new BusinessException("课程不存在");
        }
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以录入成绩");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private String requiredString(Map<String, Object> parameters, int maxLength, String... keys) {
        String value = optionalString(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        if (value.length() > maxLength) {
            throw new BusinessException(keys[0] + " 长度不能超过 " + maxLength);
        }
        return value;
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
        Integer parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(keys[0] + " 必须是整数");
            }
        }
        if (parsed <= 0) {
            throw new BusinessException(keys[0] + " 不合法");
        }
        return parsed;
    }

    private BigDecimal requiredDecimal(Map<String, Object> parameters, String... keys) {
        BigDecimal value = optionalDecimal(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(keys[0] + " 不能小于 0");
        }
        return value;
    }

    private BigDecimal optionalDecimal(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是数字");
        }
    }

    private void requirePositive(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(fieldName + " 必须大于 0");
        }
    }

    private String optionalString(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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
