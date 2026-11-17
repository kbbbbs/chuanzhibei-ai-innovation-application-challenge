package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.CourseResourceRemovePlanTool;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CourseResourceRemoveActionExecutor implements AgentActionExecutor {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cupk.aiacademy.service.PermissionService permissionService;
    private static final String ACTION_CODE = "course.resource.remove";
    private static final Set<String> DEFAULT_FIELDS = Set.of("resourceType", "resourceUrl", "videoUrl", "content");

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public CourseResourceRemoveActionExecutor(LessonMapper lessonMapper,
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
        return "移除课程资源";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return CourseResourceRemovePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer lessonId = requiredInteger(parameters, "lessonId", "lesson_id", "id");
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        Chapter chapter = requireManagedChapter(lesson.getChapterId(), user);
        Set<String> fields = parseRemoveFields(parameters);

        Map<String, Object> oldValues = snapshot(lesson);
        for (String field : fields) {
            clearField(lesson, field);
        }
        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        if (row <= 0) {
            throw new BusinessException("移除课程资源失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonId", lesson.getId());
        data.put("chapterId", lesson.getChapterId());
        data.put("courseId", chapter.getCourseId());
        data.put("title", lesson.getTitle());
        data.put("removedFields", fields);
        data.put("oldValues", oldValues);
        data.put("reason", optionalString(parameters, "reason"));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程资源已移除",
                data,
                LocalDateTime.now()
        );
    }

    private Set<String> parseRemoveFields(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "removeFields", "remove_fields", "resourceFields", "resource_fields", "fields");
        if (value == null || String.valueOf(value).isBlank()) {
            return DEFAULT_FIELDS;
        }
        Set<String> fields = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                fields.add(normalizeField(item));
            }
        } else {
            String[] parts = String.valueOf(value).split("[,，]");
            for (String part : parts) {
                fields.add(normalizeField(part));
            }
        }
        fields.remove("");
        if (fields.isEmpty()) {
            throw new BusinessException("移除字段不能为空");
        }
        return fields;
    }

    private String normalizeField(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (normalized) {
            case "resourcetype", "type", "资源类型" -> "resourceType";
            case "resourceurl", "url", "资源地址", "资料地址", "文档地址", "链接" -> "resourceUrl";
            case "videourl", "video", "视频", "视频地址" -> "videoUrl";
            case "content", "正文", "内容" -> "content";
            default -> throw new BusinessException("不支持移除字段：" + value);
        };
    }

    private void clearField(Lesson lesson, String field) {
        switch (field) {
            case "resourceType" -> lesson.setResourceType(null);
            case "resourceUrl" -> lesson.setResourceUrl(null);
            case "videoUrl" -> lesson.setVideoUrl(null);
            case "content" -> lesson.setContent(null);
            default -> throw new BusinessException("不支持移除字段：" + field);
        }
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
            throw new BusinessException("只能移除当前登录教师负责课程下的课时资源");
        }
        return chapter;
    }

    private Map<String, Object> snapshot(Lesson lesson) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("resourceType", lesson.getResourceType());
        values.put("resourceUrl", lesson.getResourceUrl());
        values.put("videoUrl", lesson.getVideoUrl());
        values.put("content", lesson.getContent());
        return values;
    }

    private boolean isActiveChapter(Chapter chapter) {
        return chapter != null && Chapter.STATUS_NORMAL == defaultChapterStatus(chapter.getStatus());
    }

    private int defaultChapterStatus(Integer status) {
        return status == null ? Chapter.STATUS_NORMAL : status;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || !isTeacher(user)) {
            throw new BusinessException("仅所属教师可以移除课程资源");
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
        if (value == null || value <= 0) {
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
