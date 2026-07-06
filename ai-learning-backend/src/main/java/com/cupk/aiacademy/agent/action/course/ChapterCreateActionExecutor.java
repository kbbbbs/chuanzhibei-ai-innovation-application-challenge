package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ChapterCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "chapter.create";

    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public ChapterCreateActionExecutor(ChapterMapper chapterMapper, CourseMapper courseMapper) {
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建章节";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer courseId = requiredInteger(parameters, "courseId", "course_id");
        Course course = requireManagedCourse(courseId, user, "只能在当前登录教师负责的课程下创建章节");
        String title = requiredString(parameters, "title", 100);
        Integer sortOrder = optionalPositiveInteger(parameters, "sortOrder", "sort_order");

        LocalDateTime now = LocalDateTime.now();
        Chapter chapter = new Chapter();
        chapter.setCourseId(course.getId());
        chapter.setTitle(title);
        chapter.setDescription(optionalString(parameters, "description", 1000));
        chapter.setSortOrder(sortOrder);
        chapter.setStatus(Chapter.STATUS_NORMAL);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);

        int row = chapterMapper.insert(chapter);
        if (row <= 0) {
            throw new BusinessException("创建章节失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chapterId", chapter.getId());
        data.put("courseId", chapter.getCourseId());
        data.put("courseTitle", course.getTitle());
        data.put("title", chapter.getTitle());
        data.put("sortOrder", chapter.getSortOrder());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "章节已创建",
                data,
                LocalDateTime.now()
        );
    }

    private Course requireManagedCourse(Integer courseId, AuthUtils.LoginUser user, String permissionMessage) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException(permissionMessage);
        }
        return course;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以创建章节");
        }
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

    private String optionalString(Map<String, Object> parameters, String key, int maxLength) {
        Object value = firstValue(parameters, key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (maxLength > 0 && text.length() > maxLength) {
            throw new BusinessException(key + " 长度不能超过 " + maxLength);
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
