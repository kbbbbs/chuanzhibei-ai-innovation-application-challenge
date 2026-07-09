package com.cupk.aiacademy.agent.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.GradeRecord;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NaturalLanguageQueryService {
    private final NaturalLanguageEntityResolver entityResolver;
    private final NaturalLanguageTimeParser timeParser;
    private final AgentToolSupport support;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final GradeRecordMapper gradeRecordMapper;
    private final LearningProgressMapper learningProgressMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final ExamSessionMapper examSessionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final UserMapper userMapper;

    public NaturalLanguageQueryService(NaturalLanguageEntityResolver entityResolver,
                                       NaturalLanguageTimeParser timeParser,
                                       AgentToolSupport support,
                                       TeachingClassMapper teachingClassMapper,
                                       ClassStudentMapper classStudentMapper,
                                       HomeworkMapper homeworkMapper,
                                       HomeworkSubmitMapper homeworkSubmitMapper,
                                       GradeRecordMapper gradeRecordMapper,
                                       LearningProgressMapper learningProgressMapper,
                                       ChapterMapper chapterMapper,
                                       LessonMapper lessonMapper,
                                       ExamSessionMapper examSessionMapper,
                                       ExamRecordMapper examRecordMapper,
                                       UserMapper userMapper) {
        this.entityResolver = entityResolver;
        this.timeParser = timeParser;
        this.support = support;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.homeworkMapper = homeworkMapper;
        this.homeworkSubmitMapper = homeworkSubmitMapper;
        this.gradeRecordMapper = gradeRecordMapper;
        this.learningProgressMapper = learningProgressMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.examSessionMapper = examSessionMapper;
        this.examRecordMapper = examRecordMapper;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public String answer(String queryText, int maxItems) {
        String query = queryText == null ? "" : queryText.trim();
        if (query.isEmpty()) {
            return "自然语言查询内容为空。";
        }
        int limit = Math.max(1, Math.min(20, maxItems));
        NaturalLanguageTimeRange timeRange = timeParser.parse(query);
        QueryContext context = buildContext(query, timeRange, limit);

        String disambiguation = disambiguationMessage(query, context);
        if (!disambiguation.isBlank()) {
            return disambiguation;
        }
        if (isHomeworkSubmissionQuery(query)) {
            return homeworkSubmissionAnswer(context, limit);
        }
        if (isExamStatsQuery(query)) {
            return examStatsAnswer(context, limit);
        }
        if (isGradeQuery(query)) {
            return gradeAnswer(context, limit);
        }
        if (isLearningProgressQuery(query)) {
            return learningProgressAnswer(context, limit);
        }
        if (isCourseStructureQuery(query)) {
            return courseStructureAnswer(context, limit);
        }
        if (isExamQuery(query)) {
            return examAnswer(context, limit);
        }
        if (isHomeworkQuery(query)) {
            return homeworkAnswer(context, limit);
        }
        if (isCourseStatsQuery(query)) {
            return courseStatsAnswer(context);
        }
        return courseAnswer(context, limit);
    }

    private QueryContext buildContext(String query, NaturalLanguageTimeRange timeRange, int limit) {
        List<Course> courses = entityResolver.resolveCourses(query, limit);
        List<Integer> courseIds = ids(courses, Course::getId);
        List<TeachingClass> classes = entityResolver.resolveTeachingClasses(query, courseIds, limit);
        List<Integer> classIds = ids(classes, TeachingClass::getId);
        if (classIds.isEmpty()) {
            classIds = entityResolver.classIdsForCourses(courses);
        }
        List<User> students = entityResolver.resolveStudents(query, classIds, limit);
        if (students.isEmpty() && containsAny(query, "我", "我的")) {
            AuthUtils.LoginUser user = UserContext.get();
            if (support.isStudent(user) && user.getUserId() != null) {
                User currentUser = userMapper.selectById(user.getUserId());
                if (currentUser != null) {
                    students = List.of(currentUser);
                }
            }
        }
        List<Homework> homeworks = entityResolver.resolveHomeworks(query, classIds, timeRange, limit);
        return new QueryContext(query, timeRange, courses, classes, classIds, students, homeworks);
    }

    private String disambiguationMessage(String query, QueryContext context) {
        if (isGradeQuery(query) || isHomeworkSubmissionQuery(query) || isLearningProgressQuery(query)) {
            if (context.students().size() > 1) {
                return "匹配到多个学生，请补充学号、手机号或更完整姓名后重试：\n" + userLines(context.students());
            }
        }
        if ((isHomeworkSubmissionQuery(query) || isCourseStructureQuery(query) || isCourseStatsQuery(query))
                && context.courses().size() > 3) {
            return "匹配到多门课程，请补充更完整课程名后重试：\n" + courseLines(context.courses());
        }
        return "";
    }

    private String homeworkSubmissionAnswer(QueryContext context, int limit) {
        List<Homework> homeworks = context.homeworks();
        if (homeworks.isEmpty()) {
            homeworks = listHomeworksByClasses(context.classIds(), context.timeRange(), limit);
        }
        if (homeworks.isEmpty()) {
            return withScope(context, "未查询到匹配作业，无法统计提交情况。");
        }
        List<String> lines = new ArrayList<>();
        for (Homework homework : homeworks) {
            List<ClassStudent> classStudents = listActiveClassStudents(homework.getClassId());
            if (!context.students().isEmpty()) {
                Set<Integer> targetStudentIds = new LinkedHashSet<>(ids(context.students(), User::getId));
                classStudents = classStudents.stream()
                        .filter(item -> targetStudentIds.contains(item.getStudentId()))
                        .toList();
            }
            List<HomeworkSubmit> submits = listSubmits(homework.getId());
            Set<Integer> submittedStudentIds = submits.stream()
                    .map(HomeworkSubmit::getStudentId)
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Integer> allStudentIds = classStudents.stream()
                    .map(ClassStudent::getStudentId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
            List<Integer> missingStudentIds = allStudentIds.stream()
                    .filter(id -> !submittedStudentIds.contains(id))
                    .toList();
            lines.add("作业=" + homework.getTitle()
                    + "；作业ID=" + homework.getId()
                    + "；应交人数=" + allStudentIds.size()
                    + "；已交人数=" + submittedStudentIds.size()
                    + "；未交人数=" + missingStudentIds.size()
                    + "；提交率=" + percent(submittedStudentIds.size(), allStudentIds.size())
                    + "；未交学生=" + userNames(missingStudentIds, 12));
        }
        return withScope(context, String.join("\n", lines));
    }

    private String gradeAnswer(QueryContext context, int limit) {
        QueryWrapper<GradeRecord> query = new QueryWrapper<>();
        applyGradeVisibility(query);
        if (!context.students().isEmpty()) {
            query.in("student_id", ids(context.students(), User::getId));
        }
        if (!context.courses().isEmpty()) {
            query.in("course_id", ids(context.courses(), Course::getId));
        }
        if (!context.classIds().isEmpty()) {
            query.in("class_id", context.classIds());
        }
        applyTimeRange(query, "create_time", context.timeRange());
        applyKeywordFilter(query, entityResolver.extractTerms(context.query()), "grade_name", "source_type", "grade_comment");
        query.orderByDesc("create_time").orderByDesc("id").last(limit(limit));
        List<GradeRecord> records = gradeRecordMapper.selectList(query);
        if (records.isEmpty()) {
            return withScope(context, "未查询到匹配成绩记录。");
        }
        BigDecimal total = BigDecimal.ZERO;
        int scoreCount = 0;
        List<String> lines = new ArrayList<>();
        for (GradeRecord record : records) {
            if (record.getScore() != null) {
                total = total.add(record.getScore());
                scoreCount++;
            }
            lines.add("成绩ID=" + record.getId()
                    + "；学生=" + userName(record.getStudentId())
                    + "；课程ID=" + value(record.getCourseId())
                    + "；教学班ID=" + value(record.getClassId())
                    + "；名称=" + value(record.getGradeName())
                    + "；分数=" + value(record.getScore()) + "/" + value(record.getFullScore())
                    + "；评语=" + support.summarize(record.getComment(), 80));
        }
        String stat = scoreCount == 0 ? "" : "\n统计：记录数=" + records.size()
                + "；平均分=" + total.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP);
        return withScope(context, String.join("\n", lines) + stat);
    }

    private String learningProgressAnswer(QueryContext context, int limit) {
        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (!context.students().isEmpty()) {
            query.in("student_id", ids(context.students(), User::getId));
        }
        if (!context.classIds().isEmpty()) {
            query.in("class_id", context.classIds());
        }
        applyTimeRange(query, "last_learn_time", context.timeRange());
        query.orderByDesc("last_learn_time").orderByDesc("id").last(limit(limit));
        List<LearningProgress> records = learningProgressMapper.selectList(query);
        if (records.isEmpty()) {
            return withScope(context, "未查询到匹配学习进度。");
        }
        int totalProgress = 0;
        int unfinished = 0;
        int weak = 0;
        List<String> lines = new ArrayList<>();
        for (LearningProgress record : records) {
            int progress = record.getProgress() == null ? 0 : record.getProgress();
            totalProgress += progress;
            if (progress < 100) {
                unfinished++;
            }
            if (progress < 60) {
                weak++;
            }
            Lesson lesson = record.getLessonId() == null ? null : lessonMapper.selectById(record.getLessonId());
            lines.add("学生=" + userName(record.getStudentId())
                    + "；课时=" + (lesson == null ? record.getLessonId() : lesson.getTitle())
                    + "；教学班ID=" + value(record.getClassId())
                    + "；进度=" + progress + "%"
                    + "；已学秒数=" + value(record.getLearnedSeconds())
                    + "；最近学习=" + value(record.getLastLearnTime()));
        }
        String stat = "统计：记录数=" + records.size()
                + "；平均进度=" + (totalProgress / records.size()) + "%"
                + "；未完成课时数=" + unfinished
                + "；低于60%课时数=" + weak;
        return withScope(context, stat + "\n" + String.join("\n", lines));
    }

    private String courseStructureAnswer(QueryContext context, int limit) {
        List<Course> courses = context.courses();
        if (courses.isEmpty()) {
            return "未解析到课程，请补充课程名称。";
        }
        List<String> lines = new ArrayList<>();
        for (Course course : courses.stream().limit(limit).toList()) {
            QueryWrapper<Chapter> chapterQuery = new QueryWrapper<>();
            chapterQuery.eq("course_id", course.getId()).eq("status", Chapter.STATUS_NORMAL)
                    .orderByAsc("sort_order").orderByAsc("id");
            List<Chapter> chapters = chapterMapper.selectList(chapterQuery);
            lines.add("课程=" + course.getTitle() + "；课程ID=" + course.getId() + "；章节数=" + chapters.size());
            for (Chapter chapter : chapters) {
                QueryWrapper<Lesson> lessonQuery = new QueryWrapper<>();
                lessonQuery.eq("chapter_id", chapter.getId()).orderByAsc("sort_order").orderByAsc("id");
                List<Lesson> lessons = lessonMapper.selectList(lessonQuery);
                lines.add("  章节=" + chapter.getTitle() + "；章节ID=" + chapter.getId() + "；课时数=" + lessons.size());
                for (Lesson lesson : lessons.stream().limit(8).toList()) {
                    lines.add("    课时=" + lesson.getTitle() + "；课时ID=" + lesson.getId()
                            + "；类型=" + value(lesson.getResourceType())
                            + "；状态=" + value(lesson.getStatus()));
                }
            }
        }
        return withScope(context, String.join("\n", lines));
    }

    private String examAnswer(QueryContext context, int limit) {
        if (containsAny(context.query(), "记录", "成绩", "分数", "结果")) {
            return examRecordAnswer(context, limit);
        }
        QueryWrapper<ExamSession> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            if (context.classIds().isEmpty()) {
                return withScope(context, "没有可查询的考试范围。");
            }
            query.in("class_id", context.classIds());
        } else if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        }
        if (!context.classIds().isEmpty()) {
            query.in("class_id", context.classIds());
        }
        applyTimeRange(query, "start_time", context.timeRange());
        applyKeywordFilter(query, entityResolver.extractTerms(context.query()), "title", "description");
        query.orderByDesc("start_time").orderByDesc("id").last(limit(limit));
        List<ExamSession> sessions = examSessionMapper.selectList(query);
        if (sessions.isEmpty()) {
            return withScope(context, "未查询到匹配考试安排。");
        }
        List<String> lines = sessions.stream()
                .map(session -> "考试ID=" + session.getId()
                        + "；标题=" + value(session.getTitle())
                        + "；教学班ID=" + value(session.getClassId())
                        + "；开始=" + value(session.getStartTime())
                        + "；结束=" + value(session.getEndTime())
                        + "；状态=" + value(session.getStatus()))
                .toList();
        return withScope(context, String.join("\n", lines));
    }

    private String examRecordAnswer(QueryContext context, int limit) {
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        if (!context.students().isEmpty()) {
            query.in("student_id", ids(context.students(), User::getId));
        }
        applyTimeRange(query, "submit_time", context.timeRange());
        query.orderByDesc("submit_time").orderByDesc("id").last(limit(limit));
        List<ExamRecord> records = examRecordMapper.selectList(query);
        if (records.isEmpty()) {
            return withScope(context, "未查询到匹配考试记录。");
        }
        List<String> lines = records.stream()
                .map(record -> "考试记录ID=" + record.getId()
                        + "；学生=" + userName(record.getStudentId())
                        + "；考试ID=" + value(record.getExamSessionId())
                        + "；提交=" + value(record.getSubmitTime())
                        + "；总分=" + value(record.getTotalScore())
                        + "；状态=" + value(record.getStatus()))
                .toList();
        return withScope(context, String.join("\n", lines));
    }

    private String examStatsAnswer(QueryContext context, int limit) {
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        if (!context.students().isEmpty()) {
            query.in("student_id", ids(context.students(), User::getId));
        }
        applyTimeRange(query, "submit_time", context.timeRange());
        query.orderByDesc("submit_time").orderByDesc("id").last(limit(limit));
        List<ExamRecord> records = examRecordMapper.selectList(query);
        if (records.isEmpty()) {
            return withScope(context, "未查询到匹配考试记录，无法统计。");
        }
        List<ExamRecord> scored = records.stream()
                .filter(r -> r.getTotalScore() != null)
                .toList();
        if (scored.isEmpty()) {
            return withScope(context, "匹配到 " + records.size() + " 条考试记录，但暂无有效分数。");
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = scored.get(0).getTotalScore();
        BigDecimal min = scored.get(0).getTotalScore();
        for (ExamRecord record : scored) {
            total = total.add(record.getTotalScore());
            if (record.getTotalScore().compareTo(max) > 0) {
                max = record.getTotalScore();
            }
            if (record.getTotalScore().compareTo(min) < 0) {
                min = record.getTotalScore();
            }
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);
        BigDecimal threshold = average.multiply(new BigDecimal("0.6")).setScale(2, RoundingMode.HALF_UP);
        List<ExamRecord> lowScored = scored.stream()
                .filter(r -> r.getTotalScore().compareTo(threshold) < 0)
                .toList();
        String stat = "统计：记录数=" + scored.size()
                + "；平均分=" + average
                + "；最高分=" + max
                + "；最低分=" + min
                + "；低分学生数=" + lowScored.size();
        List<String> lines = new ArrayList<>();
        lines.add(stat);
        if (!lowScored.isEmpty()) {
            lines.add("低分学生：");
            for (ExamRecord record : lowScored) {
                lines.add("  " + userName(record.getStudentId()) + "=" + record.getTotalScore());
            }
        }
        return withScope(context, String.join("\n", lines));
    }

    private String homeworkAnswer(QueryContext context, int limit) {
        List<Homework> homeworks = context.homeworks();
        if (homeworks.isEmpty()) {
            homeworks = listHomeworksByClasses(context.classIds(), context.timeRange(), limit);
        }
        if (homeworks.isEmpty()) {
            return withScope(context, "未查询到匹配作业。");
        }
        List<String> lines = homeworks.stream()
                .map(homework -> "作业ID=" + homework.getId()
                        + "；标题=" + value(homework.getTitle())
                        + "；教学班ID=" + value(homework.getClassId())
                        + "；截止时间=" + value(homework.getDeadline())
                        + "；满分=" + value(homework.getFullScore())
                        + "；状态=" + value(homework.getStatus())
                        + "；内容=" + support.summarize(homework.getContent(), 100))
                .toList();
        return withScope(context, String.join("\n", lines));
    }

    private String courseStatsAnswer(QueryContext context) {
        List<Course> courses = context.courses();
        if (courses.isEmpty()) {
            return "未解析到课程，无法统计课程数据。";
        }
        List<String> lines = new ArrayList<>();
        for (Course course : courses) {
            int chapterCount = countByCourse(course.getId(), "chapter");
            int lessonCount = countLessonsByCourse(course.getId());
            int classCount = countByCourse(course.getId(), "class");
            int homeworkCount = countHomeworksByCourse(course.getId());
            lines.add("课程=" + course.getTitle()
                    + "；课程ID=" + course.getId()
                    + "；章节数=" + chapterCount
                    + "；课时数=" + lessonCount
                    + "；教学班数=" + classCount
                    + "；作业数=" + homeworkCount);
        }
        return withScope(context, String.join("\n", lines));
    }

    private String courseAnswer(QueryContext context, int limit) {
        List<Course> courses = context.courses();
        if (courses.isEmpty()) {
            return "未查询到匹配课程。当前工具已解析关键词="
                    + entityResolver.extractTerms(context.query())
                    + "；可尝试补充课程名、教师名、班级名或时间条件。";
        }
        return withScope(context, courseLines(courses.stream().limit(limit).toList()));
    }

    private List<Homework> listHomeworksByClasses(List<Integer> classIds, NaturalLanguageTimeRange timeRange, int limit) {
        QueryWrapper<Homework> query = new QueryWrapper<>();
        if (!support.applyHomeworkVisibility(query)) {
            return List.of();
        }
        if (classIds != null && !classIds.isEmpty()) {
            query.in("class_id", classIds);
        }
        applyTimeRange(query, "deadline", timeRange);
        query.orderByDesc("deadline").orderByDesc("id").last(limit(limit));
        return homeworkMapper.selectList(query);
    }

    private List<ClassStudent> listActiveClassStudents(Integer classId) {
        if (classId == null) {
            return List.of();
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId).eq("status", 1);
        return classStudentMapper.selectList(query);
    }

    private List<HomeworkSubmit> listSubmits(Integer homeworkId) {
        if (homeworkId == null) {
            return List.of();
        }
        QueryWrapper<HomeworkSubmit> query = new QueryWrapper<>();
        query.eq("homework_id", homeworkId);
        return homeworkSubmitMapper.selectList(query);
    }

    private void applyGradeVisibility(QueryWrapper<GradeRecord> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!support.isAdmin(user)) {
            query.eq("id", -1);
        }
    }

    private <T> void applyKeywordFilter(QueryWrapper<T> query, List<String> terms, String... columns) {
        if (terms == null || terms.isEmpty() || columns == null || columns.length == 0) {
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

    private <T> void applyTimeRange(QueryWrapper<T> query, String column, NaturalLanguageTimeRange timeRange) {
        if (timeRange == null || !timeRange.present()) {
            return;
        }
        if (timeRange.start() != null) {
            query.ge(column, timeRange.start());
        }
        if (timeRange.end() != null) {
            query.le(column, timeRange.end());
        }
    }

    private int countByCourse(Integer courseId, String type) {
        if (courseId == null) {
            return 0;
        }
        if ("chapter".equals(type)) {
            QueryWrapper<Chapter> query = new QueryWrapper<>();
            query.eq("course_id", courseId).eq("status", Chapter.STATUS_NORMAL);
            return Math.toIntExact(chapterMapper.selectCount(query));
        }
        if ("class".equals(type)) {
            QueryWrapper<TeachingClass> query = new QueryWrapper<>();
            query.eq("course_id", courseId);
            return Math.toIntExact(teachingClassMapper.selectCount(query));
        }
        return 0;
    }

    private int countLessonsByCourse(Integer courseId) {
        QueryWrapper<Chapter> chapterQuery = new QueryWrapper<>();
        chapterQuery.eq("course_id", courseId).eq("status", Chapter.STATUS_NORMAL);
        List<Integer> chapterIds = chapterMapper.selectList(chapterQuery).stream()
                .map(Chapter::getId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (chapterIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<Lesson> lessonQuery = new QueryWrapper<>();
        lessonQuery.in("chapter_id", chapterIds);
        return Math.toIntExact(lessonMapper.selectCount(lessonQuery));
    }

    private int countHomeworksByCourse(Integer courseId) {
        QueryWrapper<TeachingClass> classQuery = new QueryWrapper<>();
        classQuery.eq("course_id", courseId);
        List<Integer> classIds = teachingClassMapper.selectList(classQuery).stream()
                .map(TeachingClass::getId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (classIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<Homework> homeworkQuery = new QueryWrapper<>();
        homeworkQuery.in("class_id", classIds);
        return Math.toIntExact(homeworkMapper.selectCount(homeworkQuery));
    }

    private String withScope(QueryContext context, String content) {
        List<String> scope = new ArrayList<>();
        if (!context.timeRange().source().isBlank()) {
            scope.add("时间=" + context.timeRange().source() + "(" + context.timeRange().start() + " 至 " + context.timeRange().end() + ")");
        }
        if (!context.courses().isEmpty()) {
            scope.add("课程候选=" + context.courses().stream()
                    .limit(3)
                    .map(course -> course.getTitle() + "#" + course.getId())
                    .collect(Collectors.joining("、")));
        }
        if (!context.classes().isEmpty()) {
            scope.add("班级候选=" + context.classes().stream()
                    .limit(3)
                    .map(item -> item.getClassName() + "#" + item.getId())
                    .collect(Collectors.joining("、")));
        }
        if (!context.students().isEmpty()) {
            scope.add("学生候选=" + context.students().stream()
                    .limit(3)
                    .map(user -> displayUser(user) + "#" + user.getId())
                    .collect(Collectors.joining("、")));
        }
        return scope.isEmpty() ? content : "解析范围：" + String.join("；", scope) + "\n" + content;
    }

    private String courseLines(List<Course> courses) {
        return courses.stream()
                .map(course -> "课程ID=" + course.getId()
                        + "；名称=" + value(course.getTitle())
                        + "；教师=" + value(course.getTeacher())
                        + "；难度=" + value(course.getDifficulty())
                        + "；状态=" + value(course.getStatus())
                        + "；简介=" + support.summarize(course.getDescription(), 100))
                .collect(Collectors.joining("\n"));
    }

    private String userLines(List<User> users) {
        return users.stream()
                .map(user -> "用户ID=" + user.getId()
                        + "；用户名=" + value(user.getUsername())
                        + "；昵称=" + value(user.getNickname())
                        + "；手机号=" + value(user.getPhone()))
                .collect(Collectors.joining("\n"));
    }

    private String userNames(List<Integer> userIds, int limit) {
        if (userIds == null || userIds.isEmpty()) {
            return "无";
        }
        return userIds.stream()
                .limit(limit)
                .map(this::userName)
                .collect(Collectors.joining("、"));
    }

    private String userName(Integer userId) {
        if (userId == null) {
            return "无";
        }
        User user = userMapper.selectById(userId);
        return user == null ? String.valueOf(userId) : displayUser(user);
    }

    private String displayUser(User user) {
        if (user == null) {
            return "无";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return String.valueOf(user.getId());
    }

    private <T> List<Integer> ids(List<T> values, Function<T, Integer> mapper) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(mapper)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private String percent(int value, int total) {
        if (total <= 0) {
            return "0%";
        }
        return BigDecimal.valueOf(value * 100L)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private String limit(int limit) {
        return "LIMIT " + Math.max(1, Math.min(50, limit));
    }

    private String value(Object value) {
        return support.value(value);
    }

    private boolean isHomeworkSubmissionQuery(String query) {
        return containsAny(query, "提交率", "提交情况", "未提交", "没交", "未交", "哪些学生还没交");
    }

    private boolean isGradeQuery(String query) {
        return containsAny(query, "成绩", "分数", "得分", "平均分");
    }

    private boolean isLearningProgressQuery(String query) {
        return containsAny(query, "学习进度", "平均进度", "未完成课时", "薄弱课时");
    }

    private boolean isCourseStructureQuery(String query) {
        return containsAny(query, "章节", "课时", "课程结构", "目录");
    }

    private boolean isExamQuery(String query) {
        return containsAny(query, "考试", "测验", "考核", "考试记录");
    }

    private boolean isExamStatsQuery(String query) {
        return containsAny(query, "考试", "测验", "考核")
                && containsAny(query, "平均分", "平均成绩", "最高分", "最低分", "低分", "及格率", "统计");
    }

    private boolean isHomeworkQuery(String query) {
        return containsAny(query, "作业", "练习", "任务");
    }

    private boolean isCourseStatsQuery(String query) {
        return query.contains("统计") && containsAny(query, "课程", "章节", "课时", "教学班", "作业");
    }

    private boolean containsAny(String value, String... candidates) {
        if (value == null || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private record QueryContext(
            String query,
            NaturalLanguageTimeRange timeRange,
            List<Course> courses,
            List<TeachingClass> classes,
            List<Integer> classIds,
            List<User> students,
            List<Homework> homeworks
    ) {
        private QueryContext {
            timeRange = timeRange == null ? new NaturalLanguageTimeRange(null, null, "") : timeRange;
            courses = courses == null ? List.of() : courses;
            classes = classes == null ? List.of() : classes;
            classIds = classIds == null ? List.of() : classIds;
            students = students == null ? List.of() : students;
            homeworks = homeworks == null ? List.of() : homeworks;
        }
    }
}
