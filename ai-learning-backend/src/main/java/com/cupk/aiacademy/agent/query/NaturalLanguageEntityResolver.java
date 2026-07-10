package com.cupk.aiacademy.agent.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Deprecated(forRemoval = true)
public class NaturalLanguageEntityResolver {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9_]{2,}");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("[“\"']([^”\"']{2,80})[”\"']");
    private static final List<Pattern> NAMED_ENTITY_PATTERNS = List.of(
            Pattern.compile("(?:叫|名叫|名字叫|名称叫|标题叫)\\s*([^，,。；;？?\\n]{2,80}?)(?:的)?(?:课程|课|门课)?(?:吗|么|呢)?\\s*$"),
            Pattern.compile("(?:课程|课)(?:名称|标题|名字|名)?\\s*(?:是|为|叫|=|:|：)\\s*([^，,。；;？?\\n]{2,80})"),
            Pattern.compile("(?:有没有|是否有|有无)\\s*(?:一门|一个|个)?\\s*([^，,。；;？?\\n]{2,80}?)(?:的)?(?:课程|课|门课)?(?:吗|么|呢)?\\s*$")
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "帮我", "请问", "查询", "查一下", "查看", "统计", "有没有", "是否有", "有无",
            "当前", "现在", "一门", "一门课", "一个", "个", "叫", "名叫", "名字叫", "名称叫",
            "哪些", "什么", "怎么", "如何",
            "课程", "这门课", "门课", "教学班", "班级", "学生", "老师", "教师", "作业", "练习",
            "考试", "试卷", "成绩", "分数", "提交", "提交率", "学习", "进度", "章节", "课时",
            "最近", "本周", "上周", "下周", "本月", "上月", "下月", "今天", "昨天", "明天",
            "的课"
    );

    private final CourseMapper courseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final HomeworkMapper homeworkMapper;
    private final UserMapper userMapper;
    private final AgentToolSupport support;

    public NaturalLanguageEntityResolver(CourseMapper courseMapper,
                                         TeachingClassMapper teachingClassMapper,
                                         ClassStudentMapper classStudentMapper,
                                         HomeworkMapper homeworkMapper,
                                         UserMapper userMapper,
                                         AgentToolSupport support) {
        this.courseMapper = courseMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.homeworkMapper = homeworkMapper;
        this.userMapper = userMapper;
        this.support = support;
    }

    public List<Course> resolveCourses(String text, int limit) {
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!support.applyCourseVisibility(query)) {
            return List.of();
        }
        applyKeywordFilter(query, extractTerms(text), "title", "description", "teacher", "difficulty");
        query.orderByDesc("id").last(limit(limit));
        return courseMapper.selectList(query);
    }

    public List<TeachingClass> resolveTeachingClasses(String text, List<Integer> courseIds, int limit) {
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        if (!support.applyTeachingClassVisibility(query)) {
            return List.of();
        }
        if (courseIds != null && !courseIds.isEmpty()) {
            query.in("course_id", courseIds);
        }
        applyKeywordFilter(query, extractTerms(text), "class_name", "semester", "description");
        query.orderByDesc("id").last(limit(limit));
        return teachingClassMapper.selectList(query);
    }

    public List<Homework> resolveHomeworks(String text,
                                           List<Integer> classIds,
                                           NaturalLanguageTimeRange timeRange,
                                           int limit) {
        QueryWrapper<Homework> query = new QueryWrapper<>();
        if (!support.applyHomeworkVisibility(query)) {
            return List.of();
        }
        if (classIds != null && !classIds.isEmpty()) {
            query.in("class_id", classIds);
        }
        applyKeywordFilter(query, extractTerms(text), "title", "content");
        if (timeRange != null && timeRange.present()) {
            if (timeRange.start() != null) {
                query.ge("deadline", timeRange.start());
            }
            if (timeRange.end() != null) {
                query.le("deadline", timeRange.end());
            }
        }
        query.orderByDesc("deadline").orderByDesc("id").last(limit(limit));
        return homeworkMapper.selectList(query);
    }

    public List<User> resolveStudents(String text, List<Integer> classIds, int limit) {
        Set<Integer> scopedStudentIds = new LinkedHashSet<>();
        if (classIds != null && !classIds.isEmpty()) {
            QueryWrapper<ClassStudent> classStudentQuery = new QueryWrapper<>();
            classStudentQuery.in("class_id", classIds).eq("status", 1);
            classStudentMapper.selectList(classStudentQuery).forEach(item -> {
                if (item.getStudentId() != null) {
                    scopedStudentIds.add(item.getStudentId());
                }
            });
            if (scopedStudentIds.isEmpty()) {
                return List.of();
            }
        }

        QueryWrapper<User> query = new QueryWrapper<>();
        if (!scopedStudentIds.isEmpty()) {
            query.in("id", scopedStudentIds);
        }
        applyKeywordFilter(query, extractTerms(text), "username", "nickname", "email", "phone");
        query.orderByDesc("id").last(limit(limit));
        return userMapper.selectList(query);
    }

    public List<Integer> classIdsForCourses(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }
        List<Integer> courseIds = courses.stream()
                .map(Course::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        if (!support.applyTeachingClassVisibility(query)) {
            return List.of();
        }
        query.in("course_id", courseIds);
        return teachingClassMapper.selectList(query).stream()
                .map(TeachingClass::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    public List<String> extractTerms(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher quotedMatcher = QUOTED_PATTERN.matcher(text);
        while (quotedMatcher.find() && terms.size() < 6) {
            addNamedEntityTerm(terms, quotedMatcher.group(1));
        }
        for (Pattern pattern : NAMED_ENTITY_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find() && terms.size() < 6) {
                addNamedEntityTerm(terms, matcher.group(1));
            }
        }
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(text);
        while (tokenMatcher.find() && terms.size() < 8) {
            addTerm(terms, tokenMatcher.group());
        }
        return new ArrayList<>(terms);
    }

    private <T> void applyKeywordFilter(QueryWrapper<T> query, List<String> terms, String... columns) {
        if (terms == null || terms.isEmpty() || columns == null || columns.length == 0) {
            return;
        }
        query.and(wrapper -> {
            boolean first = true;
            for (String term : terms) {
                for (int i = 0; i < columns.length; i++) {
                    if (first) {
                        wrapper.like(columns[i], term);
                        first = false;
                    } else {
                        wrapper.or().like(columns[i], term);
                    }
                }
            }
        });
    }

    private void addTerm(Set<String> terms, String value) {
        String term = cleanTerm(value);
        if (!term.isEmpty()) {
            terms.add(term);
        }
    }

    private void addNamedEntityTerm(Set<String> terms, String value) {
        String term = cleanNamedEntityTerm(value);
        if (!term.isEmpty()) {
            terms.add(term);
        }
    }

    private String cleanNamedEntityTerm(String value) {
        String text = value == null ? "" : value.trim();
        text = text.replaceFirst("^(?:一门|一个|个|这门|该门|这|这个)\\s*", "");
        text = text.replaceFirst("\\s*(?:的)?(?:课程|课|门课)(?:吗|么|呢)?\\s*$", "");
        text = text.replaceFirst("\\s*(?:吗|么|呢)$", "");
        return text.trim().length() < 2 ? "" : text.trim();
    }

    private String cleanTerm(String value) {
        String text = value == null ? "" : value.trim();
        for (String stopWord : STOP_WORDS) {
            text = text.replace(stopWord, "");
        }
        text = text.trim();
        return text.length() < 2 ? "" : text;
    }

    private String limit(int limit) {
        return "LIMIT " + Math.max(1, Math.min(50, limit));
    }
}
