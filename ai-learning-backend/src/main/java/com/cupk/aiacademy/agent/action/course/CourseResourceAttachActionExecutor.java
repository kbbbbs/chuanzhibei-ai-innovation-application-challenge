package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
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
public class CourseResourceAttachActionExecutor implements AgentActionExecutor {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cupk.aiacademy.service.PermissionService permissionService;
    private static final String ACTION_CODE = "course.resource.attach";

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public CourseResourceAttachActionExecutor(LessonMapper lessonMapper,
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
        return "挂载课程资源";
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

        Map<String, Object> oldValues = snapshot(lesson);
        boolean changed = applyResourceFields(lesson, parameters);
        if (!changed) {
            throw new BusinessException("至少需要提供一个资源字段");
        }

        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        if (row <= 0) {
            throw new BusinessException("挂载课程资源失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonId", lesson.getId());
        data.put("chapterId", lesson.getChapterId());
        data.put("courseId", chapter.getCourseId());
        data.put("title", lesson.getTitle());
        data.put("oldResourceType", oldValues.get("resourceType"));
        data.put("newResourceType", lesson.getResourceType());
        data.put("oldResourceUrl", oldValues.get("resourceUrl"));
        data.put("newResourceUrl", lesson.getResourceUrl());
        data.put("oldVideoUrl", oldValues.get("videoUrl"));
        data.put("newVideoUrl", lesson.getVideoUrl());
        data.put("oldContent", oldValues.get("content"));
        data.put("newContent", lesson.getContent());
        data.put("reason", optionalString(parameters, "reason"));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程资源已挂载",
                data,
                LocalDateTime.now()
        );
    }

    private boolean applyResourceFields(Lesson lesson, Map<String, Object> parameters) {
        boolean changed = false;
        if (hasAnyKey(parameters, "resourceType", "resource_type")) {
            lesson.setResourceType(optionalString(parameters, new String[]{"resourceType", "resource_type"}, 30));
            changed = true;
        }
        if (hasAnyKey(parameters, "resourceUrl", "resource_url")) {
            lesson.setResourceUrl(optionalString(parameters, new String[]{"resourceUrl", "resource_url"}, 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "videoUrl", "video_url")) {
            lesson.setVideoUrl(optionalString(parameters, new String[]{"videoUrl", "video_url"}, 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "content")) {
            lesson.setContent(optionalString(parameters, "content"));
            changed = true;
        }
        return changed;
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
            throw new BusinessException("只能给当前登录教师负责课程下的课时挂载资源");
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
            throw new BusinessException("仅所属教师可以挂载课程资源");
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

    private String optionalString(Map<String, Object> parameters, String key) {
        Object value = firstValue(parameters, key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String optionalString(Map<String, Object> parameters, String[] keys, int maxLength) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (maxLength > 0 && text.length() > maxLength) {
            throw new BusinessException(keys[0] + " 长度不能超过 " + maxLength);
        }
        return text;
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
