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
public class LessonUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "lesson.update";

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public LessonUpdateActionExecutor(LessonMapper lessonMapper,
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
        return "修改课时";
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
        Chapter originalChapter = requireManagedChapter(lesson.getChapterId(), user, "只能修改当前登录教师负责课程下的课时");

        Map<String, Object> oldValues = snapshot(lesson, originalChapter);
        boolean changed = applyUpdates(lesson, parameters, user);
        if (!changed) {
            throw new BusinessException("至少需要提供一个可修改的课时字段");
        }

        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        if (row <= 0) {
            throw new BusinessException("修改课时失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonId", lesson.getId());
        data.put("oldChapterId", oldValues.get("chapterId"));
        data.put("newChapterId", lesson.getChapterId());
        data.put("oldCourseId", oldValues.get("courseId"));
        data.put("newCourseId", courseIdOfChapter(lesson.getChapterId()));
        data.put("oldTitle", oldValues.get("title"));
        data.put("newTitle", lesson.getTitle());
        data.put("oldResourceType", oldValues.get("resourceType"));
        data.put("newResourceType", lesson.getResourceType());
        data.put("oldSortOrder", oldValues.get("sortOrder"));
        data.put("newSortOrder", lesson.getSortOrder());
        data.put("oldStatus", oldValues.get("status"));
        data.put("newStatus", lesson.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课时已修改",
                data,
                LocalDateTime.now()
        );
    }

    private boolean applyUpdates(Lesson lesson, Map<String, Object> parameters, AuthUtils.LoginUser user) {
        boolean changed = false;
        if (hasAnyKey(parameters, "chapterId", "chapter_id")) {
            Integer chapterId = requiredInteger(parameters, "chapterId", "chapter_id");
            Chapter targetChapter = requireManagedChapter(chapterId, user, "只能把课时移动到当前登录教师负责课程的章节下");
            lesson.setChapterId(targetChapter.getId());
            changed = true;
        }
        if (hasAnyKey(parameters, "title")) {
            lesson.setTitle(requiredString(parameters, "title", 100));
            changed = true;
        }
        if (hasAnyKey(parameters, "resourceType", "resource_type")) {
            lesson.setResourceType(optionalString(parameters, new String[]{"resourceType", "resource_type"}, 30));
            changed = true;
        }
        if (hasAnyKey(parameters, "resourceUrl", "resource_url")) {
            lesson.setResourceUrl(optionalString(parameters, new String[]{"resourceUrl", "resource_url"}, 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "content")) {
            lesson.setContent(optionalString(parameters, "content"));
            changed = true;
        }
        if (hasAnyKey(parameters, "videoUrl", "video_url")) {
            lesson.setVideoUrl(optionalString(parameters, new String[]{"videoUrl", "video_url"}, 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "duration")) {
            lesson.setDuration(nonNegativeInteger(parameters, "duration"));
            changed = true;
        }
        if (hasAnyKey(parameters, "sortOrder", "sort_order")) {
            lesson.setSortOrder(optionalPositiveInteger(parameters, "sortOrder", "sort_order"));
            changed = true;
        }
        if (hasAnyKey(parameters, "status")) {
            Integer status = requiredInteger(parameters, "status");
            requireValidStatus(status);
            lesson.setStatus(status);
            changed = true;
        }
        return changed;
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

    private Integer courseIdOfChapter(Integer chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        return isActiveChapter(chapter) ? chapter.getCourseId() : null;
    }

    private Map<String, Object> snapshot(Lesson lesson, Chapter chapter) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("chapterId", lesson.getChapterId());
        values.put("courseId", chapter.getCourseId());
        values.put("title", lesson.getTitle());
        values.put("resourceType", lesson.getResourceType());
        values.put("resourceUrl", lesson.getResourceUrl());
        values.put("content", lesson.getContent());
        values.put("videoUrl", lesson.getVideoUrl());
        values.put("duration", lesson.getDuration());
        values.put("sortOrder", lesson.getSortOrder());
        values.put("status", lesson.getStatus());
        return values;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改课时");
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

    private String optionalString(Map<String, Object> parameters, String key, int maxLength) {
        return optionalString(parameters, new String[]{key}, maxLength);
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
