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
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.OperationLog;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.teacherapply.TeacherApplication;
import com.cupk.aiacademy.teacherapply.TeacherApplicationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaturalLanguageQueryServiceTest {
    @Mock private NaturalLanguageEntityResolver entityResolver;
    @Mock private NaturalLanguageTimeParser timeParser;
    @Mock private AgentToolSupport support;
    @Mock private CourseMapper courseMapper;
    @Mock private CourseCategoryMapper categoryMapper;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private HomeworkMapper homeworkMapper;
    @Mock private HomeworkSubmitMapper homeworkSubmitMapper;
    @Mock private GradeRecordMapper gradeRecordMapper;
    @Mock private LearningProgressMapper learningProgressMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private LessonMapper lessonMapper;
    @Mock private ExamSessionMapper examSessionMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private NoticeMapper noticeMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private PaperMapper paperMapper;
    @Mock private TeacherApplicationMapper teacherApplicationMapper;
    @Mock private UserMapper userMapper;

    private NaturalLanguageQueryService newService() {
        return new NaturalLanguageQueryService(entityResolver, timeParser, support,
                courseMapper, categoryMapper, teachingClassMapper, classStudentMapper, homeworkMapper, homeworkSubmitMapper,
                gradeRecordMapper, learningProgressMapper, chapterMapper, lessonMapper,
                examSessionMapper, examRecordMapper, examPaperMapper, noticeMapper, operationLogMapper, paperMapper,
                teacherApplicationMapper, userMapper);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void homeworkSubmissionStatusSummarizesCrossChainQuery() {
        Course course = new Course();
        course.setId(11);
        course.setTitle("Python 数据分析基础");
        when(entityResolver.resolveCourses(any(), anyInt())).thenReturn(List.of(course));
        when(entityResolver.resolveTeachingClasses(any(), anyList(), anyInt())).thenReturn(List.of());
        when(entityResolver.classIdsForCourses(anyList())).thenReturn(List.of(5));
        when(timeParser.parse(any())).thenReturn(new NaturalLanguageTimeRange(null, null, ""));

        Homework hw = new Homework();
        hw.setId(21);
        hw.setClassId(5);
        hw.setTitle("数据清洗");
        hw.setFullScore(new BigDecimal("100"));
        when(entityResolver.resolveHomeworks(any(), anyList(), any(), anyInt()))
                .thenReturn(List.of(hw));

        ClassStudent cs = new ClassStudent();
        cs.setClassId(5);
        cs.setStudentId(7);
        cs.setStatus(1);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs));

        HomeworkSubmit submit = new HomeworkSubmit();
        submit.setHomeworkId(21);
        submit.setStudentId(7);
        when(homeworkSubmitMapper.selectList(any())).thenReturn(List.of(submit));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查 Python 数据分析基础 这门课的作业提交情况", 10);

        assertTrue(answer.contains("数据清洗"));
        assertTrue(answer.contains("已交人数=1") || answer.contains("提交率=100"));
        assertTrue(answer.contains("未交人数=0"));
    }

    @Test
    void examStatsComputesAverageAndLowScoreStudents() {
        when(entityResolver.resolveCourses(any(), anyInt())).thenReturn(List.of());
        when(entityResolver.resolveTeachingClasses(any(), anyList(), anyInt())).thenReturn(List.of());
        when(entityResolver.classIdsForCourses(anyList())).thenReturn(List.of());
        when(timeParser.parse(any())).thenReturn(new NaturalLanguageTimeRange(null, null, ""));

        ExamRecord high = new ExamRecord();
        high.setStudentId(7);
        high.setTotalScore(new BigDecimal("90"));
        ExamRecord mid = new ExamRecord();
        mid.setStudentId(8);
        mid.setTotalScore(new BigDecimal("70"));
        ExamRecord low = new ExamRecord();
        low.setStudentId(9);
        low.setTotalScore(new BigDecimal("30"));
        when(examRecordMapper.selectList(any())).thenReturn(List.of(high, mid, low));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("统计这门考试的平均分和低分学生", 10);

        assertTrue(answer.contains("平均分"));
        assertTrue(answer.contains("最高分=90"));
        assertTrue(answer.contains("低分学生数=1"));
        assertTrue(answer.contains("30"));
    }

    @Test
    void categoryListQueryReturnsCurrentCategories() {
        CourseCategory computer = category(3, "计算机组成原理");
        CourseCategory java = category(4, "Java 后端");
        when(categoryMapper.selectList(any())).thenReturn(List.of(computer, java));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("显示当前的课程分类都有什么", 10);

        assertTrue(answer.contains("课程分类ID=3"));
        assertTrue(answer.contains("计算机组成原理"));
        assertTrue(answer.contains("Java 后端"));
    }

    @Test
    void categoryListQueryUsesExpandedLimit() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1, "计算机"),
                category(2, "Java"),
                category(3, "数据库"),
                category(4, "人工智能"),
                category(5, "软件工程"),
                category(6, "计算机组成原理")
        ));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        NaturalLanguageQueryService service = newService();
        service.answer("查询现在有哪些课程分类", 5);

        ArgumentCaptor<QueryWrapper<CourseCategory>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(categoryMapper, atLeastOnce()).selectList(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .map(QueryWrapper::getCustomSqlSegment)
                .anyMatch(segment -> segment != null && segment.contains("LIMIT 50")));
    }

    @Test
    void categoryCourseQueryFindsCoursesByCategoryName() {
        CourseCategory category = category(3, "计算机组成原理");
        when(entityResolver.extractTerms(any())).thenReturn(List.of("计算机组成原理"));
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(categoryMapper.selectById(3)).thenReturn(category);
        when(support.applyCourseVisibility(any())).thenReturn(true);
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        Course course = new Course();
        course.setId(21);
        course.setTitle("计算机组成原理实验");
        course.setCategoryId(3);
        course.setTeacher("张老师");
        course.setDifficulty("中等");
        course.setStatus(1);
        course.setDescription("硬件组成与指令系统");
        when(courseMapper.selectList(any())).thenReturn(List.of(course));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查询属于计算机组成原理的类别的课", 10);

        assertTrue(answer.contains("课程分类候选=计算机组成原理#3"));
        assertTrue(answer.contains("课程ID=21"));
        assertTrue(answer.contains("计算机组成原理实验"));
        assertTrue(answer.contains("分类=计算机组成原理#3"));
    }

    @Test
    void categoryCourseQueryUnderCategoryPhraseFindsCategoryTerm() throws Exception {
        CourseCategory category = category(3, "计算机组成原理");
        when(entityResolver.extractTerms(any())).thenReturn(List.of("计算机组成原理分类下有"));
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(categoryMapper.selectById(3)).thenReturn(category);
        when(support.applyCourseVisibility(any())).thenReturn(true);
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        Course course = new Course();
        course.setId(21);
        course.setTitle("计算机组成原理实验");
        course.setCategoryId(3);
        course.setStatus(1);
        when(courseMapper.selectList(any())).thenReturn(List.of(course));

        NaturalLanguageQueryService service = newService();
        String query = "查询计算机组成原理分类下有哪些课";
        List<String> terms = extractCategoryTerms(service, query);
        String answer = service.answer(query, 10);

        assertTrue(terms.contains("计算机组成原理"));
        assertTrue(answer.contains("课程ID=21"));
        assertTrue(answer.contains("计算机组成原理实验"));
    }

    @Test
    void unsupportedQueryReturnsCapabilityBoundaryInsteadOfNotFound() {
        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查看当前平台有哪些审计日志", 10);

        assertTrue(answer.contains("尚未接入"));
        assertTrue(answer.contains("审计日志"));
    }

    @Test
    void noticeQueryReturnsUnifiedResultWithScope() {
        when(entityResolver.extractTerms(any())).thenReturn(List.of("停课"));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        Notice notice = new Notice();
        notice.setId(31);
        notice.setTitle("停课通知");
        notice.setContent("本周课程暂停一次");
        notice.setNoticeType("course");
        notice.setStatus(1);
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查一下停课通知", 10);

        assertTrue(answer.contains("查询范围：类型=通知查询"));
        assertTrue(answer.contains("通知ID=31"));
        assertTrue(answer.contains("停课通知"));
    }

    @Test
    void examPaperQueryReturnsUnifiedResultWithScope() {
        when(entityResolver.extractTerms(any())).thenReturn(List.of("期末"));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        ExamPaper paper = new ExamPaper();
        paper.setId(41);
        paper.setTitle("Java 期末试卷");
        paper.setCourseId(11);
        paper.setTeacherId(7);
        paper.setStatus(1);
        when(examPaperMapper.selectList(any())).thenReturn(List.of(paper));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查一下期末试卷", 10);

        assertTrue(answer.contains("查询范围：类型=试卷查询"));
        assertTrue(answer.contains("试卷ID=41"));
        assertTrue(answer.contains("Java 期末试卷"));
    }

    @Test
    void academicPaperQueryReturnsUnifiedResultWithScope() {
        when(entityResolver.extractTerms(any())).thenReturn(List.of("Transformer"));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        Paper paper = new Paper();
        paper.setId(51);
        paper.setTitle("Attention Is All You Need");
        paper.setYear(2017);
        paper.setSource("local");
        when(paperMapper.selectList(any())).thenReturn(List.of(paper));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查一下 Transformer 论文", 10);

        assertTrue(answer.contains("查询范围：类型=论文查询"));
        assertTrue(answer.contains("论文ID=51"));
        assertTrue(answer.contains("Attention Is All You Need"));
    }

    @Test
    void teacherApplicationQueryRequiresAdminAndReturnsApplications() {
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser();
        admin.setUserId(1);
        admin.setRoleCode("admin");
        UserContext.set(admin);
        when(support.isAdmin(admin)).thenReturn(true);
        when(entityResolver.extractTerms(any())).thenReturn(List.of("张老师"));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        TeacherApplication application = new TeacherApplication();
        application.setId(61);
        application.setUsername("zhang");
        application.setNickname("张老师");
        application.setStatus(0);
        application.setReason("申请授课");
        when(teacherApplicationMapper.selectList(any())).thenReturn(List.of(application));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查看待审核的教师申请，张老师", 10);

        assertTrue(answer.contains("查询范围：类型=教师申请查询"));
        assertTrue(answer.contains("教师申请ID=61"));
        assertTrue(answer.contains("待审核#0"));
    }

    @Test
    void teachingClassQueryReturnsUnifiedResultWithScope() {
        when(support.applyTeachingClassVisibility(any())).thenReturn(true);
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(71);
        teachingClass.setCourseId(21);
        teachingClass.setTeacherId(7);
        teachingClass.setClassName("Java 1 班");
        teachingClass.setSemester("2026春");
        teachingClass.setStatus(1);
        when(entityResolver.resolveTeachingClasses(any(), anyList(), anyInt())).thenReturn(List.of(teachingClass));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查一下 Java 1 班这个教学班", 10);

        assertTrue(answer.contains("查询范围：类型=教学班查询"));
        assertTrue(answer.contains("教学班ID=71"));
        assertTrue(answer.contains("Java 1 班"));
    }

    @Test
    void importStatusQueryReturnsRecentImportLogsForTeacher() {
        AuthUtils.LoginUser teacher = new AuthUtils.LoginUser();
        teacher.setUserId(7);
        teacher.setRoleCode("teacher");
        UserContext.set(teacher);
        when(support.isTeacher(teacher)).thenReturn(true);
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        OperationLog log = new OperationLog();
        log.setId(81);
        log.setUserId(7);
        log.setOperation("usage.request");
        log.setMethod("POST");
        log.setUrl("/api/imports/questions/preview");
        log.setParams("{\"status\":200,\"summary\":\"POST /api/imports/questions/preview\"}");
        when(operationLogMapper.selectList(any())).thenReturn(List.of(log));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("看看最近导入有没有成功", 10);

        assertTrue(answer.contains("查询范围：类型=导入状态查询"));
        assertTrue(answer.contains("操作日志ID=81"));
        assertTrue(answer.contains("/api/imports/questions/preview"));
    }

    @Test
    void categoryCourseQueryAsksForCategoryDisambiguation() {
        CourseCategory first = category(3, "计算机");
        CourseCategory second = category(4, "计算机组成原理");
        when(entityResolver.extractTerms(any())).thenReturn(List.of("计算机"));
        when(categoryMapper.selectList(any())).thenReturn(List.of(first, second));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查计算机相关的课", 10);

        assertTrue(answer.contains("匹配到多个课程分类"));
        assertTrue(answer.contains("计算机组成原理"));
    }

    @Test
    void courseDependentQueryAsksForCourseDisambiguation() {
        Course first = new Course();
        first.setId(11);
        first.setTitle("Java 基础");
        first.setTeacher("张老师");
        Course second = new Course();
        second.setId(12);
        second.setTitle("Java 进阶");
        second.setTeacher("李老师");
        when(entityResolver.resolveCourses(any(), anyInt())).thenReturn(List.of(first, second));
        when(support.value(any())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0).toString());
        when(support.summarize(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0) == null
                ? "无" : invocation.getArgument(0));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("统计 Java 这门课的章节数", 10);

        assertTrue(answer.contains("匹配到多门课程"));
        assertTrue(answer.contains("Java 基础"));
        assertTrue(answer.contains("教师=张老师"));
    }

    @Test
    void emptyQueryReturnsMessage() {
        NaturalLanguageQueryService service = newService();
        String answer = service.answer("", 10);
        assertTrue(answer.contains("为空") || answer.contains("空"));
    }

    private CourseCategory category(Integer id, String name) {
        CourseCategory category = new CourseCategory();
        category.setId(id);
        category.setName(name);
        category.setDescription(name + "方向课程");
        return category;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractCategoryTerms(NaturalLanguageQueryService service, String query) throws Exception {
        Method method = NaturalLanguageQueryService.class.getDeclaredMethod("extractCategoryTerms", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, query);
    }
}
