package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.ChapterDeletePlanTool;
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
public class ChapterDeleteActionExecutor implements AgentActionExecutor {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cupk.aiacademy.service.PermissionService permissionService;
    private static final String ACTION_CODE = "chapter.delete";

    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;

    public ChapterDeleteActionExecutor(ChapterMapper chapterMapper,
                                       CourseMapper courseMapper,
                                       LessonMapper lessonMapper) {
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.lessonMapper = lessonMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除章节";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ChapterDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Integer chapterId = requiredInteger(context.parameters(), "chapterId", "chapter_id", "id");
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
            throw new BusinessException("只能删除当前登录教师负责课程下的章节");
        }

        int lessonCount = countLessons(chapter.getId());
        Chapter update = new Chapter();
        update.setId(chapter.getId());
        update.setStatus(Chapter.STATUS_DELETED);
        update.setUpdatedAt(LocalDateTime.now());
        int row = chapterMapper.updateById(update);
        if (row <= 0) {
            throw new BusinessException("删除章节失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chapterId", chapter.getId());
        data.put("courseId", chapter.getCourseId());
        data.put("courseTitle", course.getTitle());
        data.put("title", chapter.getTitle());
        data.put("sortOrder", chapter.getSortOrder());
        data.put("oldStatus", defaultChapterStatus(chapter.getStatus()));
        data.put("newStatus", Chapter.STATUS_DELETED);
        data.put("softDeleted", true);
        data.put("affectedLessonCount", lessonCount);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "章节已删除",
                data,
                LocalDateTime.now()
        );
    }

    private int countLessons(Integer chapterId) {
        QueryWrapper<Lesson> query = new QueryWrapper<>();
        query.eq("chapter_id", chapterId);
        return Math.toIntExact(lessonMapper.selectCount(query));
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || !isTeacher(user)) {
            throw new BusinessException("仅所属教师可以删除章节");
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
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
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
