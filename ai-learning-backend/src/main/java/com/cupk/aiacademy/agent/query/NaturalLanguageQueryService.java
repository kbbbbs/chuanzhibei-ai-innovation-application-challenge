package com.cupk.aiacademy.agent.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.entity.Paper;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.mapper.PaperMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.GradeRecord;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.OperationLog;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.teacherapply.TeacherApplication;
import com.cupk.aiacademy.teacherapply.TeacherApplicationMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Deprecated(forRemoval = true)
public class NaturalLanguageQueryService {
    private static final int CATEGORY_LIST_LIMIT = 50;
    private static final Pattern CATEGORY_COURSE_PATTERN = Pattern.compile(
            "(?:查询|查一下|查看|显示|列出|找一下|找|请问|帮我)?\\s*"
                    + "(.{2,80}?)(?:的)?(?:课程分类|分类|类别|方向|类)"
                    + "(?:下|下面|里|里面|中|中的)?(?:有(?:哪些|什么)?|有哪些|都有什么|的)?(?:课程|课|门课)?\\s*$");

    private final NaturalLanguageEntityResolver entityResolver;
    private final NaturalLanguageTimeParser timeParser;
    private final AgentToolSupport support;
    private final CourseMapper courseMapper;
    private final CourseCategoryMapper categoryMapper;
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
    private final ExamPaperMapper examPaperMapper;
    private final NoticeMapper noticeMapper;
    private final OperationLogMapper operationLogMapper;
    private final PaperMapper paperMapper;
    private final TeacherApplicationMapper teacherApplicationMapper;
    private final UserMapper userMapper;

    public NaturalLanguageQueryService(NaturalLanguageEntityResolver entityResolver,
                                       NaturalLanguageTimeParser timeParser,
                                       AgentToolSupport support,
                                       CourseMapper courseMapper,
                                       CourseCategoryMapper categoryMapper,
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
                                       ExamPaperMapper examPaperMapper,
                                       NoticeMapper noticeMapper,
                                       OperationLogMapper operationLogMapper,
                                       PaperMapper paperMapper,
                                       TeacherApplicationMapper teacherApplicationMapper,
                                       UserMapper userMapper) {
        this.entityResolver = entityResolver;
        this.timeParser = timeParser;
        this.support = support;
        this.courseMapper = courseMapper;
        this.categoryMapper = categoryMapper;
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
        this.examPaperMapper = examPaperMapper;
        this.noticeMapper = noticeMapper;
        this.operationLogMapper = operationLogMapper;
        this.paperMapper = paperMapper;
        this.teacherApplicationMapper = teacherApplicationMapper;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public String answer(String queryText, int maxItems) {
        String query = queryText == null ? "" : queryText.trim();
        if (query.isEmpty()) {
            return "自然语言查询内容为空。";
        }
        int limit = Math.max(1, Math.min(20, maxItems));
        boolean categoryListQuery = isCategoryListQuery(query);
        boolean categoryCourseQuery = isCategoryCourseQuery(query);
        int contextLimit = categoryListQuery ? CATEGORY_LIST_LIMIT : limit;
        NaturalLanguageTimeRange timeRange = timeParser.parse(query);
        QueryContext context = buildContext(query, timeRange, contextLimit);

        String disambiguation = disambiguationMessage(query, context);
        if (!disambiguation.isBlank()) {
            return disambiguation;
        }
        if (categoryCourseQuery) {
            return categoryCoursesAnswer(context, limit);
        }
        if (categoryListQuery) {
            return categoryListAnswer(context, CATEGORY_LIST_LIMIT);
        }
        if (isNoticeQuery(query)) {
            return noticeAnswer(context, limit);
        }
        if (isExamPaperQuery(query)) {
            return examPaperAnswer(context, limit);
        }
        if (isAcademicPaperQuery(query)) {
            return academicPaperAnswer(context, limit);
        }
        if (isTeacherApplicationQuery(query)) {
            return teacherApplicationAnswer(context, limit);
        }
        if (isImportStatusQuery(query)) {
            return importStatusAnswer(context, limit);
        }
        if (isTeachingClassQuery(query)) {
            return teachingClassAnswer(context, limit);
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
        if (isCourseQuery(query, context)) {
            return courseAnswer(context, limit);
        }
        if (isQueryLike(query)) {
            return unsupportedQueryAnswer(query);
        }
        return "";
    }

    private QueryContext buildContext(String query, NaturalLanguageTimeRange timeRange, int limit) {
        List<CourseCategory> categories = resolveCategories(query, limit);
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
        return new QueryContext(query, timeRange, categories, courses, classes, classIds, students, homeworks);
    }

    private String disambiguationMessage(String query, QueryContext context) {
        if (isCategoryCourseQuery(query) && context.categories().size() > 1) {
            return "匹配到多个课程分类，请补充更完整分类名后重试：\n" + categoryLines(context.categories());
        }
        if (requiresSingleCourse(query) && context.courses().size() > 1 && !isBroadListQuery(query)) {
            return "匹配到多门课程，请补充更完整课程名、教师名或分类后重试：\n" + courseLines(context.courses());
        }
        if (requiresSingleClass(query) && context.classes().size() > 1 && !isBroadListQuery(query)) {
            return "匹配到多个教学班，请补充更完整班级名、课程名或学期后重试：\n" + teachingClassLines(context.classes());
        }
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

    private String categoryListAnswer(QueryContext context, int limit) {
        QueryWrapper<CourseCategory> query = new QueryWrapper<>();
        query.orderByAsc("sort_order").orderByAsc("id").last(limit(limit));
        List<CourseCategory> categories = categoryMapper.selectList(query);
        if (categories == null || categories.isEmpty()) {
            return withQueryScope(context, "课程分类列表", "t_course_category",
                    "name/description/sort_order", "未查询到课程分类，可检查分类表是否已有数据。");
        }
        return withQueryScope(context, "课程分类列表", "t_course_category",
                "name/description/sort_order", categoryLines(categories));
    }

    private String categoryCoursesAnswer(QueryContext context, int limit) {
        List<CourseCategory> categories = context.categories();
        if (categories.isEmpty()) {
            return "未匹配到课程分类。分类关键词=" + extractCategoryTerms(context.query())
                    + "；查询范围=t_course_category.name/description；可先问“当前课程分类都有什么”。";
        }
        List<Integer> categoryIds = ids(categories, CourseCategory::getId);
        if (categoryIds.isEmpty()) {
            return "匹配到课程分类，但分类 ID 为空，无法继续查询课程。";
        }

        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!support.applyCourseVisibility(query)) {
            return withQueryScope(context, "课程分类下课程", "t_course_category,t_course",
                    "t_course_category.name/description -> t_course.category_id", "没有可查询的课程范围。");
        }
        query.in("category_id", categoryIds)
                .orderByDesc("id")
                .last(limit(limit));
        List<Course> courses = courseMapper.selectList(query);
        if (courses == null || courses.isEmpty()) {
            return withQueryScope(context, "课程分类下课程", "t_course_category,t_course",
                    "t_course_category.name/description -> t_course.category_id",
                    "匹配到课程分类，但该分类下没有当前账号可见课程。");
        }
        return withQueryScope(context, "课程分类下课程", "t_course_category,t_course",
                "t_course_category.name/description -> t_course.category_id", courseLines(courses));
    }

    private String noticeAnswer(QueryContext context, int limit) {
        QueryWrapper<Notice> query = new QueryWrapper<>();
        applyNoticeVisibility(query);
        applyKeywordFilter(query, entityResolver.extractTerms(context.query()), "title", "content", "notice_type");
        query.orderByDesc("create_time").orderByDesc("id").last(limit(limit));
        List<Notice> notices = noticeMapper.selectList(query);
        if (notices == null || notices.isEmpty()) {
            return withQueryScope(context, "通知查询", "t_notice",
                    "title/content/notice_type", "未查询到匹配通知。");
        }
        return withQueryScope(context, "通知查询", "t_notice",
                "title/content/notice_type", noticeLines(notices));
    }

    private String examPaperAnswer(QueryContext context, int limit) {
        QueryWrapper<ExamPaper> query = new QueryWrapper<>();
        if (!applyExamPaperVisibility(query)) {
            return withQueryScope(context, "试卷查询", "t_exam_paper",
                    "title/description/course_id/teacher_id", "没有可查询的试卷范围。");
        }
        if (!context.courses().isEmpty()) {
            query.in("course_id", ids(context.courses(), Course::getId));
        }
        applyKeywordFilter(query, entityResolver.extractTerms(context.query()), "title", "description");
        query.orderByDesc("id").last(limit(limit));
        List<ExamPaper> papers = examPaperMapper.selectList(query);
        if (papers == null || papers.isEmpty()) {
            return withQueryScope(context, "试卷查询", "t_exam_paper",
                    "title/description/course_id/teacher_id", "未查询到匹配试卷。");
        }
        return withQueryScope(context, "试卷查询", "t_exam_paper",
                "title/description/course_id/teacher_id", examPaperLines(papers));
    }

    private String academicPaperAnswer(QueryContext context, int limit) {
        QueryWrapper<Paper> query = new QueryWrapper<>();
        applyKeywordFilter(query, entityResolver.extractTerms(context.query()), "title", "abstract_text", "venue", "source");
        query.orderByDesc("year").orderByDesc("citation_count").orderByDesc("id").last(limit(limit));
        List<Paper> papers = paperMapper.selectList(query);
        if (papers == null || papers.isEmpty()) {
            return withQueryScope(context, "论文查询", "t_paper",
                    "title/abstract_text/venue/source", "未查询到匹配论文。");
        }
        return withQueryScope(context, "论文查询", "t_paper",
                "title/abstract_text/venue/source", academicPaperLines(papers));
    }

    private String teacherApplicationAnswer(QueryContext context, int limit) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!support.isAdmin(user)) {
            return withQueryScope(context, "教师申请查询", "t_teacher_application",
                    "username/nickname/email/phone/reason/review_comment/status",
                    "只有管理员可以查询教师申请。");
        }
        QueryWrapper<TeacherApplication> query = new QueryWrapper<>();
        Integer status = teacherApplicationStatus(context.query());
        if (status != null) {
            query.eq("status", status);
        }
        applyKeywordFilter(query, entityResolver.extractTerms(context.query()),
                "username", "nickname", "email", "phone", "reason", "review_comment");
        query.orderByDesc("id").last(limit(limit));
        List<TeacherApplication> applications = teacherApplicationMapper.selectList(query);
        if (applications == null || applications.isEmpty()) {
            return withQueryScope(context, "教师申请查询", "t_teacher_application",
                    "username/nickname/email/phone/reason/review_comment/status",
                    "未查询到匹配教师申请。");
        }
        return withQueryScope(context, "教师申请查询", "t_teacher_application",
                "username/nickname/email/phone/reason/review_comment/status",
                teacherApplicationLines(applications));
    }

    private String importStatusAnswer(QueryContext context, int limit) {
        QueryWrapper<OperationLog> query = new QueryWrapper<>();
        if (!applyImportLogVisibility(query)) {
            return withQueryScope(context, "导入状态查询", "t_operation_log",
                    "url/operation/params/create_time", "只有管理员或教师可以查询导入状态。");
        }
        query.and(wrapper -> wrapper.like("url", "/api/imports").or().like("operation", "import"));
        Integer httpStatus = importHttpStatus(context.query());
        if (httpStatus != null) {
            query.like("params", "\"status\":" + httpStatus);
        }
        applyTimeRange(query, "create_time", context.timeRange());
        query.orderByDesc("id").last(limit(limit));
        List<OperationLog> logs = operationLogMapper.selectList(query);
        if (logs == null || logs.isEmpty()) {
            return withQueryScope(context, "导入状态查询", "t_operation_log",
                    "url/operation/params/create_time", "未查询到导入相关记录。");
        }
        return withQueryScope(context, "导入状态查询", "t_operation_log",
                "url/operation/params/create_time", operationLogLines(logs));
    }

    private String teachingClassAnswer(QueryContext context, int limit) {
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        if (!support.applyTeachingClassVisibility(query)) {
            return withQueryScope(context, "教学班查询", "t_teaching_class",
                    "class_name/semester/description/course_id", "没有可查询的教学班范围。");
        }
        if (!context.classes().isEmpty()) {
            query.in("id", ids(context.classes(), TeachingClass::getId));
        } else {
            if (!context.courses().isEmpty()) {
                query.in("course_id", ids(context.courses(), Course::getId));
            }
            applyKeywordFilter(query, entityResolver.extractTerms(context.query()), "class_name", "semester", "description");
        }
        query.orderByDesc("id").last(limit(limit));
        List<TeachingClass> classes = teachingClassMapper.selectList(query);
        if (classes == null || classes.isEmpty()) {
            return withQueryScope(context, "教学班查询", "t_teaching_class",
                    "class_name/semester/description/course_id", "未查询到匹配教学班。");
        }
        return withQueryScope(context, "教学班查询", "t_teaching_class",
                "class_name/semester/description/course_id", teachingClassLines(classes));
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
            return withQueryScope(context, "课程查询", "t_course",
                    "title/description/teacher/difficulty",
                    "未查询到当前账号可见范围内的匹配课程。当前工具已解析关键词="
                    + entityResolver.extractTerms(context.query())
                    + "；可尝试补充课程名、教师名、班级名或时间条件。");
        }
        return withQueryScope(context, "课程查询", "t_course",
                "title/description/teacher/difficulty",
                courseLines(courses.stream().limit(limit).toList()));
    }

    private List<CourseCategory> resolveCategories(String query, int limit) {
        List<String> terms = extractCategoryTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        QueryWrapper<CourseCategory> categoryQuery = new QueryWrapper<>();
        applyKeywordFilter(categoryQuery, terms, "name", "description");
        categoryQuery.orderByAsc("sort_order").orderByAsc("id").last(limit(limit));
        List<CourseCategory> categories = categoryMapper.selectList(categoryQuery);
        return categories == null ? List.of() : categories;
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

    private void applyNoticeVisibility(QueryWrapper<Notice> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isAdmin(user)) {
            return;
        }
        if (support.isStudent(user)) {
            List<Integer> classIds = support.findVisibleClassIds();
            if (classIds.isEmpty()) {
                query.isNull("class_id");
            } else {
                query.and(wrapper -> wrapper.isNull("class_id").or().in("class_id", classIds));
            }
            return;
        }
        if (support.isTeacher(user)) {
            List<Integer> classIds = support.findVisibleClassIds();
            query.and(wrapper -> {
                wrapper.eq("publisher_id", user.getUserId()).or().isNull("class_id");
                if (!classIds.isEmpty()) {
                    wrapper.or().in("class_id", classIds);
                }
            });
            return;
        }
        query.eq("status", 1).isNull("class_id");
    }

    private boolean applyExamPaperVisibility(QueryWrapper<ExamPaper> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            List<Integer> courseIds = support.findVisibleCourseIds();
            if (courseIds.isEmpty()) {
                return false;
            }
            query.in("course_id", courseIds);
        } else if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!support.isAdmin(user)) {
            query.eq("status", 1);
        }
        return true;
    }

    private boolean applyImportLogVisibility(QueryWrapper<OperationLog> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isAdmin(user)) {
            return true;
        }
        if (support.isTeacher(user)) {
            query.eq("user_id", user.getUserId());
            return true;
        }
        return false;
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
        if (!context.categories().isEmpty()) {
            scope.add("课程分类候选=" + context.categories().stream()
                    .limit(3)
                    .map(category -> category.getName() + "#" + category.getId())
                    .collect(Collectors.joining("、")));
        }
        if (!context.classes().isEmpty()) {
            scope.add("班级候选=" + context.classes().stream()
                    .limit(3)
                    .map(item -> item.getClassName() + "#" + item.getId())
                    .collect(Collectors.joining("、")));
        }
        String teacherCandidates = teacherCandidates(context);
        if (!teacherCandidates.isBlank()) {
            scope.add("教师候选=" + teacherCandidates);
        }
        if (!context.students().isEmpty()) {
            scope.add("学生候选=" + context.students().stream()
                    .limit(3)
                    .map(user -> displayUser(user) + "#" + user.getId())
                    .collect(Collectors.joining("、")));
        }
        return scope.isEmpty() ? content : "解析范围：" + String.join("；", scope) + "\n" + content;
    }

    private String withQueryScope(QueryContext context, String queryType, String tables, String fields, String content) {
        return withScope(context, "查询范围：类型=" + queryType
                + "；数据表=" + tables
                + "；匹配字段=" + fields
                + "\n" + content);
    }

    private String courseLines(List<Course> courses) {
        return courses.stream()
                .map(course -> "课程ID=" + course.getId()
                        + "；名称=" + value(course.getTitle())
                        + "；分类=" + categoryDisplay(course.getCategoryId())
                        + "；教师=" + value(course.getTeacher())
                        + "；难度=" + value(course.getDifficulty())
                        + "；状态=" + value(course.getStatus())
                        + "；简介=" + support.summarize(course.getDescription(), 100))
                .collect(Collectors.joining("\n"));
    }

    private String categoryLines(List<CourseCategory> categories) {
        return categories.stream()
                .map(category -> "课程分类ID=" + value(category.getId())
                        + "；名称=" + value(category.getName())
                        + "；课程数=" + countVisibleCoursesByCategory(category.getId())
                        + "；描述=" + support.summarize(category.getDescription(), 80))
                .collect(Collectors.joining("\n"));
    }

    private String teachingClassLines(List<TeachingClass> classes) {
        return classes.stream()
                .map(teachingClass -> "教学班ID=" + value(teachingClass.getId())
                        + "；课程ID=" + value(teachingClass.getCourseId())
                        + "；教师ID=" + value(teachingClass.getTeacherId())
                        + "；班级名称=" + value(teachingClass.getClassName())
                        + "；学期=" + value(teachingClass.getSemester())
                        + "；状态=" + value(teachingClass.getStatus())
                        + "；说明=" + support.summarize(teachingClass.getDescription(), 100))
                .collect(Collectors.joining("\n"));
    }

    private String noticeLines(List<Notice> notices) {
        return notices.stream()
                .map(notice -> "通知ID=" + value(notice.getId())
                        + "；发布人ID=" + value(notice.getPublisherId())
                        + "；教学班ID=" + value(notice.getClassId())
                        + "；标题=" + value(notice.getTitle())
                        + "；类型=" + value(notice.getNoticeType())
                        + "；发布时间=" + value(notice.getCreateTime())
                        + "；状态=" + value(notice.getStatus())
                        + "；内容=" + support.summarize(notice.getContent(), 120))
                .collect(Collectors.joining("\n"));
    }

    private String examPaperLines(List<ExamPaper> papers) {
        return papers.stream()
                .map(paper -> "试卷ID=" + value(paper.getId())
                        + "；课程ID=" + value(paper.getCourseId())
                        + "；教师ID=" + value(paper.getTeacherId())
                        + "；标题=" + value(paper.getTitle())
                        + "；总分=" + value(paper.getTotalScore())
                        + "；时长=" + value(paper.getDurationMinutes())
                        + "；状态=" + value(paper.getStatus())
                        + "；说明=" + support.summarize(paper.getDescription(), 100))
                .collect(Collectors.joining("\n"));
    }

    private String academicPaperLines(List<Paper> papers) {
        return papers.stream()
                .map(paper -> "论文ID=" + value(paper.getId())
                        + "；标题=" + value(paper.getTitle())
                        + "；年份=" + value(paper.getYear())
                        + "；来源=" + value(paper.getSource())
                        + "；会议/期刊=" + value(paper.getVenue())
                        + "；引用数=" + value(paper.getCitationCount())
                        + "；链接=" + value(paper.getUrl())
                        + "；摘要=" + support.summarize(paper.getAbstractText(), 160))
                .collect(Collectors.joining("\n"));
    }

    private String teacherApplicationLines(List<TeacherApplication> applications) {
        return applications.stream()
                .map(application -> "教师申请ID=" + value(application.getId())
                        + "；用户名=" + value(application.getUsername())
                        + "；昵称=" + value(application.getNickname())
                        + "；邮箱=" + value(application.getEmail())
                        + "；手机号=" + value(application.getPhone())
                        + "；状态=" + teacherApplicationStatusLabel(application.getStatus())
                        + "；申请时间=" + value(application.getCreateTime())
                        + "；审核人ID=" + value(application.getReviewerId())
                        + "；审核时间=" + value(application.getReviewTime())
                        + "；原因=" + support.summarize(application.getReason(), 100)
                        + "；审核意见=" + support.summarize(application.getReviewComment(), 80))
                .collect(Collectors.joining("\n"));
    }

    private String operationLogLines(List<OperationLog> logs) {
        return logs.stream()
                .map(log -> "操作日志ID=" + value(log.getId())
                        + "；用户ID=" + value(log.getUserId())
                        + "；操作=" + value(log.getOperation())
                        + "；方法=" + value(log.getMethod())
                        + "；地址=" + value(log.getUrl())
                        + "；时间=" + value(log.getCreateTime())
                        + "；摘要=" + support.summarize(log.getParams(), 160))
                .collect(Collectors.joining("\n"));
    }

    private int countVisibleCoursesByCategory(Integer categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return 0;
        }
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!support.applyCourseVisibility(query)) {
            return 0;
        }
        query.eq("category_id", categoryId);
        return Math.toIntExact(courseMapper.selectCount(query));
    }

    private String categoryDisplay(Integer categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return "无";
        }
        CourseCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return "未知#" + categoryId;
        }
        return value(category.getName()) + "#" + categoryId;
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

    private Integer importHttpStatus(String query) {
        if (containsAny(query, "失败", "错误", "异常", "不成功")) {
            return 400;
        }
        if (containsAny(query, "成功", "正常")) {
            return 200;
        }
        return null;
    }

    private Integer teacherApplicationStatus(String query) {
        if (containsAny(query, "待审核", "待处理", "未审核", "等待审核")) {
            return 0;
        }
        if (containsAny(query, "审核通过", "已通过", "通过的", "通过申请")) {
            return 1;
        }
        if (containsAny(query, "审核拒绝", "已拒绝", "被拒绝", "未通过", "驳回")) {
            return 2;
        }
        return null;
    }

    private String teacherApplicationStatusLabel(Integer status) {
        if (status == null) {
            return "无";
        }
        return switch (status) {
            case 0 -> "待审核#0";
            case 1 -> "通过#1";
            case 2 -> "拒绝#2";
            default -> status.toString();
        };
    }

    private boolean isHomeworkSubmissionQuery(String query) {
        return containsAny(query, "提交率", "提交情况", "未提交", "没交", "未交", "哪些学生还没交");
    }

    private boolean isCategoryCourseQuery(String query) {
        return containsAny(query, "课", "课程", "门课")
                && containsAny(query,
                "属于", "分类下", "类别下", "类下", "方向下",
                "分类的课", "类别的课", "类的课", "相关的课",
                "分类课程", "类别课程", "类课程", "相关课程");
    }

    private boolean isCategoryListQuery(String query) {
        return containsAny(query, "课程分类", "分类", "类别")
                && containsAny(query, "显示", "列出", "列表", "查看", "查询", "当前", "现在", "所有", "全部", "有哪些", "都有什么", "有什么");
    }

    private boolean isNoticeQuery(String query) {
        return containsAny(query, "通知", "公告") && isQueryLike(query);
    }

    private boolean isExamPaperQuery(String query) {
        return containsAny(query, "试卷", "卷子") && isQueryLike(query);
    }

    private boolean isAcademicPaperQuery(String query) {
        return containsAny(query, "论文", "文献", "paper", "Paper") && isQueryLike(query);
    }

    private boolean isTeacherApplicationQuery(String query) {
        return containsAny(query, "教师申请", "老师申请", "教师入驻", "入驻申请", "申请当老师")
                && isQueryLike(query);
    }

    private boolean isImportStatusQuery(String query) {
        return containsAny(query, "导入")
                && (isQueryLike(query) || containsAny(query, "状态", "进度", "成功", "失败", "错误", "记录"));
    }

    private boolean isTeachingClassQuery(String query) {
        if (!containsAny(query, "教学班", "授课班", "课程班", "班级")) {
            return false;
        }
        if (containsAny(query, "作业", "考试", "测验", "成绩", "分数", "学习进度", "学生", "成员", "名单")) {
            return false;
        }
        return isQueryLike(query);
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

    private boolean isCourseQuery(String query, QueryContext context) {
        if (!context.courses().isEmpty()) {
            return true;
        }
        return containsAny(query, "课程", "门课")
                || (query.contains("课") && !containsAny(query, "课时", "上课"));
    }

    private boolean isQueryLike(String query) {
        return containsAny(query,
                "查询", "查一下", "查", "查看", "显示", "列出", "统计", "有哪些", "都有什么",
                "有什么", "多少", "平均", "当前", "现在", "最近");
    }

    private String unsupportedQueryAnswer(String query) {
        return "当前 Agent 尚未接入该类自然语言查询能力。已识别为查询请求，但没有匹配到课程分类、课程、教学班、通知、作业、提交、成绩、学习进度、考试、试卷、论文、教师申请、导入状态或课程结构查询；请改用系统对应页面，或补充这些已支持模块的查询条件。原始请求="
                + query;
    }

    private boolean requiresSingleCourse(String query) {
        return isHomeworkSubmissionQuery(query)
                || isCourseStructureQuery(query)
                || isCourseStatsQuery(query)
                || isExamStatsQuery(query);
    }

    private boolean requiresSingleClass(String query) {
        return isTeachingClassQuery(query)
                || isHomeworkSubmissionQuery(query)
                || isHomeworkQuery(query)
                || isExamQuery(query);
    }

    private boolean isBroadListQuery(String query) {
        return containsAny(query, "有哪些", "都有什么", "所有", "全部", "列表", "列出");
    }

    private String teacherCandidates(QueryContext context) {
        LinkedHashSet<String> teachers = new LinkedHashSet<>();
        for (Course course : context.courses()) {
            if (course.getTeacher() != null && !course.getTeacher().isBlank()) {
                teachers.add(course.getTeacher());
            }
            if (course.getTeacherId() != null && course.getTeacherId() > 0) {
                teachers.add("教师ID#" + course.getTeacherId());
            }
        }
        for (TeachingClass teachingClass : context.classes()) {
            if (teachingClass.getTeacherId() != null && teachingClass.getTeacherId() > 0) {
                teachers.add("教师ID#" + teachingClass.getTeacherId());
            }
        }
        return teachers.stream().limit(3).collect(Collectors.joining("、"));
    }

    private List<String> extractCategoryTerms(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addCategoryTerm(terms, extractCategoryCourseTerm(query));
        List<String> extractedTerms = entityResolver.extractTerms(query);
        if (extractedTerms != null) {
            for (String term : extractedTerms) {
                addCategoryTerm(terms, term);
            }
        }
        if (terms.isEmpty()) {
            addCategoryTerm(terms, query);
        }
        return new ArrayList<>(terms);
    }

    private String extractCategoryCourseTerm(String query) {
        if (!isCategoryCourseQuery(query)) {
            return "";
        }
        Matcher matcher = CATEGORY_COURSE_PATTERN.matcher(query.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private void addCategoryTerm(Set<String> terms, String rawValue) {
        String term = cleanCategoryTerm(rawValue);
        if (term.length() >= 2) {
            terms.add(term);
        }
    }

    private String cleanCategoryTerm(String value) {
        String text = value == null ? "" : value.trim();
        String[] removals = {
                "课程分类", "分类名称", "都有什么", "有哪些", "有什么", "哪几类",
                "的课程", "的课", "门课", "课程", "分类", "类别", "相关", "方向",
                "帮我", "请问", "查询", "查一下", "查看", "显示", "列出", "找一下",
                "当前", "现在", "所有", "全部", "哪些", "什么", "属于",
                "下面", "下", "里面", "里", "中的", "中", "的", "类", "课"
        };
        for (String removal : removals) {
            text = text.replace(removal, "");
        }
        return text.replaceAll("[\\s，。！？?、,;；:：]+", "").trim();
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
            List<CourseCategory> categories,
            List<Course> courses,
            List<TeachingClass> classes,
            List<Integer> classIds,
            List<User> students,
            List<Homework> homeworks
    ) {
        private QueryContext {
            timeRange = timeRange == null ? new NaturalLanguageTimeRange(null, null, "") : timeRange;
            categories = categories == null ? List.of() : categories;
            courses = courses == null ? List.of() : courses;
            classes = classes == null ? List.of() : classes;
            classIds = classIds == null ? List.of() : classIds;
            students = students == null ? List.of() : students;
            homeworks = homeworks == null ? List.of() : homeworks;
        }
    }
}
