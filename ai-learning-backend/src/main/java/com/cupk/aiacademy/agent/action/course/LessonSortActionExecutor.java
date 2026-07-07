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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LessonSortActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "lesson.sort";

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public LessonSortActionExecutor(LessonMapper lessonMapper,
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
        return "调整课时排序";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer chapterId = requiredInteger(parameters, "chapterId", "chapter_id");
        Chapter chapter = requireManagedChapter(chapterId, user);
        List<SortItem> items = parseSortItems(parameters);
        Map<Integer, Lesson> lessonsById = validateItems(chapter.getId(), items);

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> changedItems = new ArrayList<>();
        for (SortItem item : items) {
            Lesson oldLesson = lessonsById.get(item.lessonId());
            Lesson lesson = new Lesson();
            lesson.setId(item.lessonId());
            lesson.setSortOrder(item.sortOrder());
            lesson.setUpdatedAt(now);
            int row = lessonMapper.updateById(lesson);
            if (row <= 0) {
                throw new BusinessException("调整课时排序失败");
            }

            Map<String, Object> changedItem = new LinkedHashMap<>();
            changedItem.put("lessonId", oldLesson.getId());
            changedItem.put("title", oldLesson.getTitle());
            changedItem.put("oldSortOrder", oldLesson.getSortOrder());
            changedItem.put("newSortOrder", item.sortOrder());
            changedItems.add(changedItem);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chapterId", chapter.getId());
        data.put("courseId", chapter.getCourseId());
        data.put("changedCount", changedItems.size());
        data.put("items", changedItems);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课时排序已调整",
                data,
                LocalDateTime.now()
        );
    }

    private Map<Integer, Lesson> validateItems(Integer chapterId, List<SortItem> items) {
        Set<Integer> seenLessonIds = new HashSet<>();
        Set<Integer> seenSortOrders = new HashSet<>();
        for (SortItem item : items) {
            if (!seenLessonIds.add(item.lessonId())) {
                throw new BusinessException("排序数据存在重复课时ID");
            }
            if (!seenSortOrders.add(item.sortOrder())) {
                throw new BusinessException("排序数据存在重复排序值");
            }
        }

        Map<Integer, Lesson> lessonsById = new LinkedHashMap<>();
        for (SortItem item : items) {
            Lesson lesson = lessonMapper.selectById(item.lessonId());
            if (lesson == null) {
                throw new BusinessException("课时不存在");
            }
            if (!chapterId.equals(lesson.getChapterId())) {
                throw new BusinessException("课时不属于该章节");
            }
            lessonsById.put(item.lessonId(), lesson);
        }
        return lessonsById;
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
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能调整当前登录教师负责课程章节下的课时排序");
        }
        return chapter;
    }

    private List<SortItem> parseSortItems(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "items", "lessons", "sortItems", "sort_items");
        if (!(value instanceof List<?> rawItems) || rawItems.isEmpty()) {
            throw new BusinessException("排序数据不能为空");
        }
        List<SortItem> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                throw new BusinessException("排序项必须是对象");
            }
            Integer lessonId = requiredInteger(rawMap, "lessonId", "lesson_id", "id");
            Integer sortOrder = requiredInteger(rawMap, "sortOrder", "sort_order");
            if (lessonId <= 0) {
                throw new BusinessException("lessonId 不能为空");
            }
            if (sortOrder <= 0) {
                throw new BusinessException("sortOrder 必须大于 0");
            }
            items.add(new SortItem(lessonId, sortOrder));
        }
        return items;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以调整课时排序");
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

    private Integer requiredInteger(Map<?, ?> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private Integer optionalInteger(Map<?, ?> parameters, String... keys) {
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

    private Object firstValue(Map<?, ?> parameters, String... keys) {
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

    private record SortItem(Integer lessonId, Integer sortOrder) {
    }
}
