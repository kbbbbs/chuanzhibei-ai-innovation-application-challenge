package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentToolService {
    private static final String COURSE_SEARCH = "course_search";
    private static final String HOMEWORK_SEARCH = "homework_search";
    private static final String LEARNING_PROGRESS = "learning_progress";
    private static final String LEARNING_PLAN = "learning_plan";

    private static final Set<String> COURSE_TOOL_NAMES = Set.of(COURSE_SEARCH, "course_query");
    private static final Set<String> HOMEWORK_TOOL_NAMES = Set.of(HOMEWORK_SEARCH, "homework_query", "assignment_search");
    private static final Set<String> LEARNING_PROGRESS_TOOL_NAMES = Set.of(LEARNING_PROGRESS, "progress_query");
    private static final Set<String> LEARNING_PLAN_TOOL_NAMES = Set.of(LEARNING_PLAN, "study_plan");
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "课程", "作业", "学习", "计划", "查询", "搜索", "生成", "推荐",
            "帮我", "请", "一下", "哪些", "什么", "怎么", "如何", "有没有", "我的"
    );
    private static final Pattern SEARCH_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]+|[A-Za-z0-9_]+");

    private final CourseMapper courseMapper;
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final LearningProgressMapper learningProgressMapper;
    private final LessonMapper lessonMapper;
    private final PermissionService permissionService;

    public AgentToolService(CourseMapper courseMapper,
                            HomeworkMapper homeworkMapper,
                            HomeworkSubmitMapper homeworkSubmitMapper,
                            LearningProgressMapper learningProgressMapper,
                            LessonMapper lessonMapper,
                            PermissionService permissionService) {
        this.courseMapper = courseMapper;
        this.homeworkMapper = homeworkMapper;
        this.homeworkSubmitMapper = homeworkSubmitMapper;
        this.learningProgressMapper = learningProgressMapper;
        this.lessonMapper = lessonMapper;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public String buildToolContext(List<String> enabledTools, String query, int maxItems, int maxCharacters) {
        if (enabledTools == null || enabledTools.isEmpty() || maxItems <= 0 || maxCharacters <= 0) {
            return "";
        }

        LinkedHashSet<String> toolNames = normalizeToolNames(enabledTools);
        if (toolNames.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (String toolName : toolNames) {
            appendToolSection(context, toolName, query, maxItems);
            if (context.length() >= maxCharacters) {
                break;
            }
        }
        return limit(context.toString(), maxCharacters);
    }

    private void appendToolSection(StringBuilder context, String toolName, String query, int maxItems) {
        String body = switch (toolName) {
            case COURSE_SEARCH -> buildCourseSearchSection(query, maxItems);
            case HOMEWORK_SEARCH -> buildHomeworkSearchSection(query, maxItems);
            case LEARNING_PROGRESS -> buildLearningProgressSection(maxItems);
            case LEARNING_PLAN -> buildLearningPlanSection(Math.max(maxItems, 10));
            default -> "";
        };
        if (isBlank(body)) {
            return;
        }
        if (!context.isEmpty()) {
            context.append("\n");
        }
        context.append("[").append(toolName).append("]\n").append(body);
    }

    private String buildCourseSearchSection(String userQuery, int maxItems) {
        Page<Course> page = new Page<>(1, maxItems);
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!applyCourseVisibility(query)) {
            return "没有可查询的课程范围。";
        }
        applyKeywordFilter(query, extractSearchTerms(userQuery), "title", "description", "teacher", "difficulty");
        query.orderByDesc("id");
        courseMapper.selectPage(page, query);
        List<Course> courses = page.getRecords();
        if (courses == null || courses.isEmpty()) {
            return "未查询到匹配课程。";
        }

        List<String> lines = new ArrayList<>();
        for (Course course : courses) {
            lines.add("课程ID=" + value(course.getId())
                    + "；名称=" + value(course.getTitle())
                    + "；教师=" + value(course.getTeacher())
                    + "；难度=" + value(course.getDifficulty())
                    + "；简介=" + summarize(course.getDescription(), 120));
        }
        return String.join("\n", lines);
    }

    private String buildHomeworkSearchSection(String userQuery, int maxItems) {
        Page<Homework> page = new Page<>(1, maxItems);
        QueryWrapper<Homework> query = new QueryWrapper<>();
        if (!applyHomeworkVisibility(query)) {
            return "没有可查询的作业范围。";
        }
        applyKeywordFilter(query, extractSearchTerms(userQuery), "title", "content");
        query.orderByDesc("deadline").orderByDesc("id");
        homeworkMapper.selectPage(page, query);
        List<Homework> homeworks = page.getRecords();
        if (homeworks == null || homeworks.isEmpty()) {
            return "未查询到匹配作业。";
        }

        AuthUtils.LoginUser user = UserContext.get();
        List<String> lines = new ArrayList<>();
        for (Homework homework : homeworks) {
            String submitStatus = buildSubmitStatus(homework.getId(), user);
            lines.add("作业ID=" + value(homework.getId())
                    + "；标题=" + value(homework.getTitle())
                    + "；教学班ID=" + value(homework.getClassId())
                    + "；截止时间=" + value(homework.getDeadline())
                    + "；满分=" + value(homework.getFullScore())
                    + "；提交情况=" + submitStatus
                    + "；内容=" + summarize(homework.getContent(), 120));
        }
        return String.join("\n", lines);
    }

    private String buildLearningProgressSection(int maxItems) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!permissionService.isStudent(user)) {
            return "学习进度工具仅返回学生本人的学习记录；当前用户没有可读取的个人学习进度。";
        }
        List<LearningProgress> progressList = listStudentProgress(user.getUserId(), maxItems);
        if (progressList.isEmpty()) {
            return "暂无学习进度记录。";
        }

        List<String> lines = new ArrayList<>();
        for (LearningProgress progress : progressList) {
            Lesson lesson = safeLesson(progress.getLessonId());
            lines.add("课时ID=" + value(progress.getLessonId())
                    + "；课时=" + value(lesson == null ? null : lesson.getTitle())
                    + "；教学班ID=" + value(progress.getClassId())
                    + "；进度=" + value(progress.getProgress()) + "%"
                    + "；已学秒数=" + value(progress.getLearnedSeconds())
                    + "；最近学习=" + value(progress.getLastLearnTime()));
        }
        return String.join("\n", lines);
    }

    private String buildLearningPlanSection(int maxItems) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!permissionService.isStudent(user)) {
            return "学习计划工具需要学生账号的个人学习进度；当前用户没有可生成计划的个人进度。";
        }
        List<LearningProgress> progressList = listStudentProgress(user.getUserId(), maxItems);
        if (progressList.isEmpty()) {
            return "暂无学习进度记录；建议先选择一门课程完成首个课时，并每天安排 30 分钟学习。";
        }

        int totalProgress = 0;
        int unfinishedCount = 0;
        List<String> weakLessons = new ArrayList<>();
        for (LearningProgress progress : progressList) {
            int value = progress.getProgress() == null ? 0 : progress.getProgress();
            totalProgress += value;
            if (value < 100) {
                unfinishedCount++;
            }
            if (value < 60) {
                Lesson lesson = safeLesson(progress.getLessonId());
                weakLessons.add(value(lesson == null ? progress.getLessonId() : lesson.getTitle()) + "(" + value + "%)");
            }
        }
        int averageProgress = totalProgress / progressList.size();
        String focus = weakLessons.isEmpty() ? "暂无明显薄弱课时" : String.join("、", weakLessons);

        return "学习记录数=" + progressList.size()
                + "；平均进度=" + averageProgress + "%"
                + "；未完成课时数=" + unfinishedCount
                + "；优先复习=" + focus
                + "；建议：先补齐低于 60% 的课时，再完成未达 100% 的课时；每天安排 30-45 分钟学习，完成后及时提交相关作业。";
    }

    private List<LearningProgress> listStudentProgress(Integer studentId, int maxItems) {
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

    private String buildSubmitStatus(Integer homeworkId, AuthUtils.LoginUser user) {
        if (!permissionService.isStudent(user) || homeworkId == null) {
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

    private boolean applyCourseVisibility(QueryWrapper<Course> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (permissionService.isStudent(user)) {
            List<Integer> courseIds = permissionService.studentCourseIds(user.getUserId());
            if (courseIds.isEmpty()) {
                return false;
            }
            query.in("id", courseIds);
        } else if (permissionService.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!permissionService.isAdmin(user)) {
            query.eq("status", 1);
        }
        return true;
    }

    private boolean applyHomeworkVisibility(QueryWrapper<Homework> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (permissionService.isStudent(user)) {
            List<Integer> classIds = permissionService.studentClassIds(user.getUserId());
            if (classIds.isEmpty()) {
                return false;
            }
            query.in("class_id", classIds);
        } else if (permissionService.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!permissionService.isAdmin(user)) {
            query.eq("status", 1);
        }
        return true;
    }

    private void applyKeywordFilter(QueryWrapper<?> query, List<String> terms, String... columns) {
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

    private LinkedHashSet<String> normalizeToolNames(List<String> enabledTools) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        for (String enabledTool : enabledTools) {
            String canonical = canonicalToolName(enabledTool);
            if (!isBlank(canonical)) {
                toolNames.add(canonical);
            }
        }
        return toolNames;
    }

    private String canonicalToolName(String toolName) {
        if (isBlank(toolName)) {
            return "";
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        if (COURSE_TOOL_NAMES.contains(normalized)) {
            return COURSE_SEARCH;
        }
        if (HOMEWORK_TOOL_NAMES.contains(normalized)) {
            return HOMEWORK_SEARCH;
        }
        if (LEARNING_PROGRESS_TOOL_NAMES.contains(normalized)) {
            return LEARNING_PROGRESS;
        }
        if (LEARNING_PLAN_TOOL_NAMES.contains(normalized)) {
            return LEARNING_PLAN;
        }
        return "";
    }

    private List<String> extractSearchTerms(String query) {
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

    private Lesson safeLesson(Integer lessonId) {
        return lessonId == null ? null : lessonMapper.selectById(lessonId);
    }

    private String value(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        return value == null ? "无" : value.toString();
    }

    private String summarize(String value, int maxLength) {
        if (isBlank(value)) {
            return "无";
        }
        return limit(value.replaceAll("\\s+", " ").trim(), maxLength);
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
