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
public class LessonCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "lesson.create";

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public LessonCreateActionExecutor(LessonMapper lessonMapper,
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
        return "创建课时";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer chapterId = requiredInteger(parameters, "chapterId", "chapter_id");
        Chapter chapter = requireManagedChapter(chapterId, user, "只能在当前登录教师负责课程的章节下创建课时");
        String title = requiredString(parameters, "title", 100);

        Integer status = optionalInteger(parameters, "status");
        if (status == null) {
            status = 1;
        }
        requireValidStatus(status);

        LocalDateTime now = LocalDateTime.now();
        Lesson lesson = new Lesson();
        lesson.setChapterId(chapter.getId());
        lesson.setTitle(title);
        lesson.setResourceType(optionalString(parameters, "resourceType", "resource_type", 30));
        lesson.setResourceUrl(optionalString(parameters, "resourceUrl", "resource_url", 255));
        lesson.setContent(optionalString(parameters, "content"));
        lesson.setVideoUrl(optionalString(parameters, "videoUrl", "video_url", 255));
        lesson.setDuration(nonNegativeInteger(parameters, "duration"));
        lesson.setSortOrder(optionalPositiveInteger(parameters, "sortOrder", "sort_order"));
        lesson.setViewCount(nonNegativeOrDefault(parameters, 0, "viewCount", "view_count"));
        lesson.setStatus(status);
        lesson.setCreatedAt(now);
        lesson.setUpdatedAt(now);

        int row = lessonMapper.insert(lesson);
        if (row <= 0) {
            throw new BusinessException("创建课时失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonId", lesson.getId());
        data.put("chapterId", lesson.getChapterId());
        data.put("courseId", chapter.getCourseId());
        data.put("title", lesson.getTitle());
        data.put("resourceType", lesson.getResourceType());
        data.put("duration", lesson.getDuration());
        data.put("sortOrder", lesson.getSortOrder());
        data.put("status", lesson.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课时已创建",
                data,
                LocalDateTime.now()
        );
    }

    private Chapter requireManagedChapter(Integer chapterId, AuthUtils.LoginUser user, String permissionMessage) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (!isActiveChapter(chapter)) {
            throw new BusinessException("章节不存在");
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException(permissionMessage);
        }
        return chapter;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以创建课时");
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

    private String requiredString(Map<String, Object> parameters, String key, int maxLength) {
        String value = optionalString(parameters, key, maxLength);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + " 不能为空");
        }
        return value;
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null || value <= 0) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private Integer optionalPositiveInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new BusinessException(keys[0] + " 必须大于 0");
        }
        return value;
    }

    private Integer nonNegativeInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BusinessException(keys[0] + " 不能小于 0");
        }
        return value;
    }

    private Integer nonNegativeOrDefault(Map<String, Object> parameters, Integer defaultValue, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            return defaultValue;
        }
        if (value < 0) {
            throw new BusinessException(keys[0] + " 不能小于 0");
        }
        return value;
    }

    private String optionalString(Map<String, Object> parameters, String key, int maxLength) {
        return optionalString(parameters, new String[]{key}, maxLength);
    }

    private String optionalString(Map<String, Object> parameters, String key1, String key2, int maxLength) {
        return optionalString(parameters, new String[]{key1, key2}, maxLength);
    }

    private String optionalString(Map<String, Object> parameters, String... keys) {
        return optionalString(parameters, keys, 0);
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
