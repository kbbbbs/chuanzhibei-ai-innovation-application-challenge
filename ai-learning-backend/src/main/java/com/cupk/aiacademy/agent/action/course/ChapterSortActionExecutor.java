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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ChapterSortActionExecutor implements AgentActionExecutor {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cupk.aiacademy.service.PermissionService permissionService;
    private static final String ACTION_CODE = "chapter.sort";

    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    public ChapterSortActionExecutor(ChapterMapper chapterMapper, CourseMapper courseMapper) {
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "调整章节排序";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer courseId = requiredInteger(parameters, "courseId", "course_id");
        Course course = requireManagedCourse(courseId, user);
        List<SortItem> items = parseSortItems(parameters);
        Map<Integer, Chapter> chaptersById = validateItems(course.getId(), items);

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> changedItems = new ArrayList<>();
        for (SortItem item : items) {
            Chapter oldChapter = chaptersById.get(item.chapterId());
            Chapter chapter = new Chapter();
            chapter.setId(item.chapterId());
            chapter.setSortOrder(item.sortOrder());
            chapter.setUpdatedAt(now);
            int row = chapterMapper.updateById(chapter);
            if (row <= 0) {
                throw new BusinessException("调整章节排序失败");
            }

            Map<String, Object> changedItem = new LinkedHashMap<>();
            changedItem.put("chapterId", oldChapter.getId());
            changedItem.put("title", oldChapter.getTitle());
            changedItem.put("oldSortOrder", oldChapter.getSortOrder());
            changedItem.put("newSortOrder", item.sortOrder());
            changedItems.add(changedItem);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("courseTitle", course.getTitle());
        data.put("changedCount", changedItems.size());
        data.put("items", changedItems);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "章节排序已调整",
                data,
                LocalDateTime.now()
        );
    }

    private Map<Integer, Chapter> validateItems(Integer courseId, List<SortItem> items) {
        Set<Integer> seenChapterIds = new HashSet<>();
        Set<Integer> seenSortOrders = new HashSet<>();
        for (SortItem item : items) {
            if (!seenChapterIds.add(item.chapterId())) {
                throw new BusinessException("排序数据存在重复章节ID");
            }
            if (!seenSortOrders.add(item.sortOrder())) {
                throw new BusinessException("排序数据存在重复排序值");
            }
        }

        Map<Integer, Chapter> chaptersById = new LinkedHashMap<>();
        for (SortItem item : items) {
            Chapter chapter = chapterMapper.selectById(item.chapterId());
            if (!isActiveChapter(chapter)) {
                throw new BusinessException("章节不存在");
            }
            if (!courseId.equals(chapter.getCourseId())) {
                throw new BusinessException("章节不属于该课程");
            }
            chaptersById.put(item.chapterId(), chapter);
        }
        return chaptersById;
    }

    private Course requireManagedCourse(Integer courseId, AuthUtils.LoginUser user) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (permissionService != null) permissionService.requireCourseContentTeacher(course.getId());
        if (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId())) {
            throw new BusinessException("只能调整当前登录教师负责课程下的章节排序");
        }
        return course;
    }

    private List<SortItem> parseSortItems(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "items", "chapters", "sortItems", "sort_items");
        if (!(value instanceof List<?> rawItems) || rawItems.isEmpty()) {
            throw new BusinessException("排序数据不能为空");
        }
        List<SortItem> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                throw new BusinessException("排序项必须是对象");
            }
            Integer chapterId = requiredInteger(rawMap, "chapterId", "chapter_id", "id");
            Integer sortOrder = requiredInteger(rawMap, "sortOrder", "sort_order");
            if (chapterId <= 0) {
                throw new BusinessException("chapterId 不能为空");
            }
            if (sortOrder <= 0) {
                throw new BusinessException("sortOrder 必须大于 0");
            }
            items.add(new SortItem(chapterId, sortOrder));
        }
        return items;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || !isTeacher(user)) {
            throw new BusinessException("仅所属教师可以调整章节排序");
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

    private record SortItem(Integer chapterId, Integer sortOrder) {
    }
}
