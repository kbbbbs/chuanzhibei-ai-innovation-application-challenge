package com.cupk.aiacademy.agent.tool.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentToolSupport {
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "课程", "作业", "学习", "计划", "查询", "搜索", "生成", "推荐",
            "帮我", "请", "一下", "哪些", "什么", "怎么", "如何", "有没有", "我的"
    );
    private static final Pattern SEARCH_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]+|[A-Za-z0-9_]+");

    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final LearningProgressMapper learningProgressMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final CourseMapper courseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;

    public AgentToolSupport(HomeworkSubmitMapper homeworkSubmitMapper,
                            LearningProgressMapper learningProgressMapper,
                            ChapterMapper chapterMapper,
                            LessonMapper lessonMapper,
                            CourseMapper courseMapper,
                            TeachingClassMapper teachingClassMapper,
                            ClassStudentMapper classStudentMapper) {
        this.homeworkSubmitMapper = homeworkSubmitMapper;
        this.learningProgressMapper = learningProgressMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.courseMapper = courseMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
    }

    public boolean applyCourseVisibility(QueryWrapper<Course> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (isStudent(user)) {
            List<Integer> courseIds = findStudentCourseIds(user.getUserId());
            if (courseIds.isEmpty()) {
                return false;
            }
            query.in("id", courseIds);
        } else if (isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!isAdmin(user)) {
            query.eq("status", 1);
        }
        return true;
    }

    public boolean applyHomeworkVisibility(QueryWrapper<Homework> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (isStudent(user)) {
            List<Integer> classIds = findStudentClassIds(user.getUserId());
            if (classIds.isEmpty()) {
                return false;
            }
            query.in("class_id", classIds);
        } else if (isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!isAdmin(user)) {
            query.eq("status", 1);
        }
        return true;
    }

    public boolean applyChapterVisibility(QueryWrapper<Chapter> query) {
        query.eq("status", Chapter.STATUS_NORMAL);
        List<Integer> courseIds = findVisibleCourseIds();
        if (courseIds == null) {
            return true;
        }
        if (courseIds.isEmpty()) {
            return false;
        }
        query.in("course_id", courseIds);
        return true;
    }

    public boolean applyLessonVisibility(QueryWrapper<Lesson> query) {
        List<Integer> chapterIds = findVisibleChapterIds();
        if (chapterIds == null) {
            return true;
        }
        if (chapterIds.isEmpty()) {
            return false;
        }
        query.in("chapter_id", chapterIds);
        return true;
    }

    public boolean applyTeachingClassVisibility(QueryWrapper<TeachingClass> query) {
        List<Integer> classIds = findVisibleClassIds();
        if (classIds == null) {
            return true;
        }
        if (classIds.isEmpty()) {
            return false;
        }
        query.in("id", classIds);
        return true;
    }

    public List<LearningProgress> listStudentProgress(Integer studentId, int maxItems) {
        Page<LearningProgress> page = new Page<>(1, maxItems);
        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        query.eq("student_id", studentId)
                .orderByDesc("last_learn_time")
                .orderByDesc("update_time")
                .orderByDesc("id");
        learningProgressMapper.selectPage(page, query);
        List<LearningProgress> records = page.getRecords();
        return records == null ? List.of() : records;
    }

    public String buildSubmitStatus(Integer homeworkId, AuthUtils.LoginUser user) {
        if (!isStudent(user) || homeworkId == null) {
            return "未按当前角色查询个人提交";
        }
        QueryWrapper<HomeworkSubmit> query = new QueryWrapper<>();
        query.eq("homework_id", homeworkId)
                .eq("student_id", user.getUserId())
                .orderByDesc("id");
        List<HomeworkSubmit> submits = homeworkSubmitMapper.selectList(query);
        if (submits == null || submits.isEmpty()) {
            return "未提交";
        }
        HomeworkSubmit submit = submits.get(0);
        return "已提交；分数=" + value(submit.getScore())
                + "；批改状态=" + value(submit.getGradeStatus())
                + "；反馈=" + summarize(submit.getFeedback(), 80);
    }

    public void applyKeywordFilter(QueryWrapper<?> query, List<String> terms, String... columns) {
        if (terms.isEmpty() || columns.length == 0) {
            return;
        }
        query.and(wrapper -> {
            boolean first = true;
            for (String term : terms) {
                for (String column : columns) {
                    if (first) {
                        wrapper.like(column, term);
                        first = false;
                    } else {
                        wrapper.or().like(column, term);
                    }
                }
            }
        });
    }

    public List<String> extractSearchTerms(String query) {
        if (isBlank(query)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = SEARCH_TOKEN_PATTERN.matcher(query);
        while (matcher.find() && terms.size() < 6) {
            String term = cleanSearchTerm(matcher.group());
            if (!isBlank(term)) {
                terms.add(term);
            }
        }
        return new ArrayList<>(terms);
    }

    public List<Integer> findVisibleCourseIds() {
        AuthUtils.LoginUser user = UserContext.get();
        if (isStudent(user)) {
            return findStudentCourseIds(user.getUserId());
        }
        if (isTeacher(user)) {
            QueryWrapper<Course> query = new QueryWrapper<>();
            query.eq("teacher_id", user.getUserId());
            return courseMapper.selectList(query).stream()
                    .map(Course::getId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }
        if (isAdmin(user)) {
            return null;
        }
        QueryWrapper<Course> query = new QueryWrapper<>();
        query.eq("status", 1);
        return courseMapper.selectList(query).stream()
                .map(Course::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    public List<Integer> findVisibleClassIds() {
        AuthUtils.LoginUser user = UserContext.get();
        if (isStudent(user)) {
            return findStudentClassIds(user.getUserId());
        }
        if (isTeacher(user)) {
            QueryWrapper<TeachingClass> query = new QueryWrapper<>();
            query.eq("teacher_id", user.getUserId());
            return teachingClassMapper.selectList(query).stream()
                    .map(TeachingClass::getId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }
        if (isAdmin(user)) {
            return null;
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.eq("status", 1);
        return teachingClassMapper.selectList(query).stream()
                .map(TeachingClass::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    public Lesson safeLesson(Integer lessonId) {
        return lessonId == null ? null : lessonMapper.selectById(lessonId);
    }

    public boolean isStudent(AuthUtils.LoginUser user) {
        return user != null && "student".equals(user.getRoleCode());
    }

    public boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    public boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    public String value(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        return value == null ? "无" : value.toString();
    }

    public String summarize(String value, int maxLength) {
        if (isBlank(value)) {
            return "无";
        }
        return limit(value.replaceAll("\\s+", " ").trim(), maxLength);
    }

    private List<Integer> findStudentCourseIds(Integer studentId) {
        List<Integer> classIds = findStudentClassIds(studentId);
        if (classIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.in("id", classIds);
        return teachingClassMapper.selectList(query).stream()
                .map(TeachingClass::getCourseId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> findVisibleChapterIds() {
        QueryWrapper<Chapter> query = new QueryWrapper<>();
        if (!applyChapterVisibility(query)) {
            return List.of();
        }
        return chapterMapper.selectList(query).stream()
                .map(Chapter::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> findStudentClassIds(Integer studentId) {
        if (studentId == null) {
            return List.of();
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("student_id", studentId).eq("status", 1);
        return classStudentMapper.selectList(query).stream()
                .map(ClassStudent::getClassId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private String cleanSearchTerm(String term) {
        if (term == null) {
            return "";
        }
        String cleaned = term.trim();
        for (String stopWord : SEARCH_STOP_WORDS) {
            cleaned = cleaned.replace(stopWord, "");
        }
        if (cleaned.length() < 2) {
            return "";
        }
        return limit(cleaned, 30);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
