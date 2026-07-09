package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.CourseStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.agent.query.AgentWriteEntityLocator;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.ClassStudent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CourseStatusActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "course.status";

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final AgentWriteEntityLocator entityLocator;

    public CourseStatusActionExecutor(CourseMapper courseMapper,
                                      ChapterMapper chapterMapper,
                                      LessonMapper lessonMapper,
                                      TeachingClassMapper teachingClassMapper,
                                      ClassStudentMapper classStudentMapper,
                                      AgentWriteEntityLocator entityLocator) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.entityLocator = entityLocator;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "变更课程状态";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return CourseStatusPlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer status = requiredInteger(parameters, "status");
        requireValidStatus(status);

        Course course = resolveCourse(parameters);
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能变更当前登录教师负责课程的状态");
        }

        Integer oldStatus = course.getStatus();
        Map<String, Object> impactPreview = buildImpactPreview(course.getId(), oldStatus, status);
        course.setStatus(status);
        course.setUpdatedAt(LocalDateTime.now());
        int row = courseMapper.updateById(course);
        if (row <= 0) {
            throw new BusinessException("变更课程状态失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("title", course.getTitle());
        data.put("teacherId", course.getTeacherId());
        data.put("oldStatus", oldStatus);
        data.put("newStatus", course.getStatus());
        data.put("reason", optionalString(parameters, "reason"));
        data.put("impactPreview", impactPreview);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程状态已变更",
                data,
                LocalDateTime.now()
        );
    }

    private Course resolveCourse(Map<String, Object> parameters) {
        Integer courseId = optionalInteger(parameters, "courseId", "course_id", "id");
        String title = optionalString(parameters, "title");
        String teacher = optionalString(parameters, "teacher");
        String difficulty = optionalString(parameters, "difficulty");
        Integer categoryId = optionalInteger(parameters, "categoryId", "category_id");
        return entityLocator.locateCourse(courseId, title, teacher, difficulty, categoryId);
    }

    private Map<String, Object> buildImpactPreview(Integer courseId, Integer oldStatus, Integer newStatus) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("courseId", courseId);
        preview.put("oldStatus", oldStatus);
        preview.put("newStatus", newStatus);
        preview.put("offline", newStatus != null && newStatus == 0);

        int chapterCount = countActiveChapters(courseId);
        int lessonCount = chapterCount > 0 ? countLessons(courseId) : 0;
        int teachingClassCount = countTeachingClasses(courseId);
        int studentCount = teachingClassCount > 0 ? countStudents(courseId) : 0;
        preview.put("affectedChapterCount", chapterCount);
        preview.put("affectedLessonCount", lessonCount);
        preview.put("affectedTeachingClassCount", teachingClassCount);
        preview.put("affectedStudentCount", studentCount);
        return preview;
    }

    private int countActiveChapters(Integer courseId) {
        QueryWrapper<Chapter> query = new QueryWrapper<>();
        query.eq("course_id", courseId).eq("status", Chapter.STATUS_NORMAL);
        return Math.toIntExact(chapterMapper.selectCount(query));
    }

    private int countLessons(Integer courseId) {
        List<Integer> chapterIds = listActiveChapterIds(courseId);
        if (chapterIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<Lesson> query = new QueryWrapper<>();
        query.in("chapter_id", chapterIds).eq("status", 1);
        return Math.toIntExact(lessonMapper.selectCount(query));
    }

    private int countTeachingClasses(Integer courseId) {
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.eq("course_id", courseId);
        return Math.toIntExact(teachingClassMapper.selectCount(query));
    }

    private int countStudents(Integer courseId) {
        List<Integer> classIds = listTeachingClassIds(courseId);
        if (classIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.in("class_id", classIds).eq("status", 1);
        return Math.toIntExact(classStudentMapper.selectCount(query));
    }

    private List<Integer> listActiveChapterIds(Integer courseId) {
        QueryWrapper<Chapter> query = new QueryWrapper<>();
        query.eq("course_id", courseId).eq("status", Chapter.STATUS_NORMAL);
        List<Chapter> chapters = chapterMapper.selectList(query);
        List<Integer> ids = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (chapter.getId() != null) {
                ids.add(chapter.getId());
            }
        }
        return ids;
    }

    private List<Integer> listTeachingClassIds(Integer courseId) {
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.eq("course_id", courseId);
        List<TeachingClass> classes = teachingClassMapper.selectList(query);
        List<Integer> ids = new ArrayList<>();
        for (TeachingClass teachingClass : classes) {
            if (teachingClass.getId() != null) {
                ids.add(teachingClass.getId());
            }
        }
        return ids;
    }

    private boolean appendEquals(QueryWrapper<Course> query, String column, String value, boolean hasLocator) {
        if (value == null || value.isBlank()) {
            return hasLocator;
        }
        query.eq(column, value.trim());
        return true;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以变更课程状态");
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
            throw new BusinessException("课程状态只能是 0、1 或 2");
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
