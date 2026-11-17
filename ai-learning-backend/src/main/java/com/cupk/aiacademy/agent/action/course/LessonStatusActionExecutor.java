package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.LessonStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LessonStatusActionExecutor implements AgentActionExecutor {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cupk.aiacademy.service.PermissionService permissionService;
    private static final String ACTION_CODE = "lesson.status";

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public LessonStatusActionExecutor(LessonMapper lessonMapper,
                                      ChapterMapper chapterMapper,
                                      CourseMapper courseMapper) {
        this.lessonMapper = lessonMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "变更课时状态";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return LessonStatusPlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer lessonId = requiredInteger(parameters, "lessonId", "lesson_id", "id");
        Integer status = requiredInteger(parameters, "status");
        requireValidStatus(status);

        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        Chapter chapter = requireManagedChapter(lesson.getChapterId(), user);

        Integer oldStatus = lesson.getStatus();
        lesson.setStatus(status);
        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        if (row <= 0) {
            throw new BusinessException("变更课时状态失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonId", lesson.getId());
        data.put("chapterId", lesson.getChapterId());
        data.put("courseId", chapter.getCourseId());
        data.put("title", lesson.getTitle());
        data.put("oldStatus", oldStatus);
        data.put("newStatus", lesson.getStatus());
        data.put("reason", optionalString(parameters, "reason"));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课时状态已变更",
                data,
                LocalDateTime.now()
        );
    }

    private Chapter requireManagedChapter(Integer chapterId, AuthUtils.LoginUser user) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (!isActiveChapter(chapter)) {
            throw new BusinessException("章节不存在");
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (permissionService != null) permissionService.requireCourseContentTeacher(course.getId());
        if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能变更当前登录教师负责课程下的课时状态");
        }
        return chapter;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || !isTeacher(user)) {
            throw new BusinessException("仅所属教师可以变更课时状态");
        }
    }

    private boolean isActiveChapter(Chapter chapter) {
        return chapter != null && Chapter.STATUS_NORMAL == defaultChapterStatus(chapter.getStatus());
    }

    private int defaultChapterStatus(Integer status) {
        return status == null ? Chapter.STATUS_NORMAL : status;
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
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
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

    private void requireValidStatus(Integer status) {
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException("课时状态只能是 0、1 或 2");
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
