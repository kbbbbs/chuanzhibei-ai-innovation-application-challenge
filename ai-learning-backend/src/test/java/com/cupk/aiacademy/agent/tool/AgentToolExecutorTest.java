package com.cupk.aiacademy.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.course.ChapterSearchTool;
import com.cupk.aiacademy.agent.tool.course.CourseSearchTool;
import com.cupk.aiacademy.agent.tool.course.CourseResourceSearchTool;
import com.cupk.aiacademy.agent.tool.course.LessonSearchTool;
import com.cupk.aiacademy.agent.tool.course.TeachingClassSearchTool;
import com.cupk.aiacademy.agent.tool.exam.ExamAnswerGradePlanTool;
import com.cupk.aiacademy.agent.tool.exam.ExamSessionClosePlanTool;
import com.cupk.aiacademy.agent.tool.exam.ExamSessionCreatePlanTool;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordCreatePlanTool;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordDeletePlanTool;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordUpdatePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkCreatePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkDeletePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkSubmissionGradePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkUpdatePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkSearchTool;
import com.cupk.aiacademy.agent.tool.learning.LearningPlanTool;
import com.cupk.aiacademy.agent.tool.learning.LearningProgressTool;
import com.cupk.aiacademy.agent.tool.notice.NoticeCreatePlanTool;
import com.cupk.aiacademy.agent.action.InMemoryAgentPendingActionStore;
import com.cupk.aiacademy.agent.tool.api.AgentToolOperationType;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutorTest {
    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private HomeworkMapper homeworkMapper;

    @Mock
    private HomeworkSubmitMapper homeworkSubmitMapper;

    @Mock
    private LearningProgressMapper learningProgressMapper;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void courseSearchReturnsVisibleStudentCourses() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(classStudent(1)));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(1, 101)));
        doAnswer(invocation -> {
            Page<Course> page = invocation.getArgument(0);
            page.setRecords(List.of(course(101, "Java 基础")));
            return page;
        }).when(courseMapper).selectPage(any(Page.class), any());

        String context = service.buildToolContext(List.of("course_search"), "帮我查 Java 课程", 5, 1000);

        assertTrue(context.contains("[course_search]"));
        assertTrue(context.contains("课程ID=101"));
        assertTrue(context.contains("Java 基础"));
    }

    @Test
    void executeToolsReturnsStructuredReadResults() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(classStudent(1)));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(1, 101)));
        doAnswer(invocation -> {
            Page<Course> page = invocation.getArgument(0);
            page.setRecords(List.of(course(101, "Java 基础")));
            return page;
        }).when(courseMapper).selectPage(any(Page.class), any());

        List<AgentToolResult> results = service.executeTools(List.of("course_query"), "帮我查 Java 课程", 5);

        assertEquals(1, results.size());
        assertEquals("course_search", results.get(0).toolName());
        assertEquals(AgentToolOperationType.READ, results.get(0).operationType());
        assertTrue(results.get(0).success());
        assertTrue(results.get(0).content().contains("课程ID=101"));
    }

    @Test
    void chapterSearchReturnsVisibleStudentChapters() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(classStudent(1)));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(1, 101)));
        doAnswer(invocation -> {
            Page<Chapter> page = invocation.getArgument(0);
            page.setRecords(List.of(chapter(201, 101, "Java 入门章节")));
            return page;
        }).when(chapterMapper).selectPage(any(Page.class), any());

        String context = service.buildToolContext(List.of("chapter_query"), "Java 入门章节", 5, 1000);

        assertTrue(context.contains("[chapter_search]"));
        assertTrue(context.contains("章节ID=201"));
        assertTrue(context.contains("Java 入门章节"));
    }

    @Test
    void learningPlanSummarizesStudentProgress() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);
        doAnswer(invocation -> {
            Page<LearningProgress> page = invocation.getArgument(0);
            page.setRecords(List.of(
                    progress(1, 40),
                    progress(2, 80)
            ));
            return page;
        }).when(learningProgressMapper).selectPage(any(Page.class), any());
        when(lessonMapper.selectById(1)).thenReturn(lesson(1, "递归入门"));

        String context = service.buildToolContext(List.of("learning_plan"), "生成学习计划", 5, 1000);

        assertTrue(context.contains("[learning_plan]"));
        assertTrue(context.contains("平均进度=60%"));
        assertTrue(context.contains("递归入门(40%)"));
        assertTrue(context.contains("每天安排 30-45 分钟学习"));
    }

    @Test
    void noticeCreatePlanReturnsStructuredWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("notice_publish_plan"),
                "帮我发布一条通知，提醒一班明天提交作业",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("notice_create_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("notice.create", result.data().get("actionCode"));
        assertEquals(true, result.data().get("requiresConfirmation"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.data().containsKey("expiresAt"));
        assertTrue(result.content().contains("操作计划=发布通知"));
        assertTrue(result.content().contains("需要确认=是"));
        assertTrue(result.content().contains("确认ID="));
    }

    @Test
    void homeworkCreatePlanReturnsStructuredWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("homework_publish_plan"),
                "帮我发布第一章作业，明天截止",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("homework_create_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("homework.create", result.data().get("actionCode"));
        assertEquals(true, result.data().get("requiresConfirmation"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.data().containsKey("expiresAt"));
        assertTrue(result.content().contains("操作计划=发布作业"));
        assertTrue(result.content().contains("确认ID="));
    }

    @Test
    void homeworkUpdatePlanReturnsStructuredWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("homework_modify_plan"),
                "把 88 号作业截止时间改到明天 18 点",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("homework_update_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("homework.update", result.data().get("actionCode"));
        assertEquals("MEDIUM", result.data().get("riskLevel"));
        assertEquals(true, result.data().get("requiresConfirmation"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.data().containsKey("expiresAt"));
        assertTrue(result.content().contains("操作计划=修改作业"));
        assertTrue(result.content().contains("确认ID="));
    }

    @Test
    void homeworkDeletePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("homework_remove_plan"),
                "删除 88 号作业",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("homework_delete_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("homework.delete", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(HomeworkDeletePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.data().containsKey("expiresAt"));
        assertTrue(result.content().contains("操作计划=删除作业"));
        assertTrue(result.content().contains("二次确认文本=" + HomeworkDeletePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void homeworkSubmissionGradePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("homework_grade_plan"),
                "批改 501 号作业提交，给 92 分，反馈完成得很好",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("homework_submission_grade_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("homework.submission.grade", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=批改作业提交"));
        assertTrue(result.content().contains("二次确认文本=" + HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void examSessionCreatePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("exam_create_plan"),
                "帮我创建一场 Java 期末考试",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("exam_session_create_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("exam.session.create", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(ExamSessionCreatePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=创建考试安排"));
        assertTrue(result.content().contains("二次确认文本=" + ExamSessionCreatePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void examSessionClosePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("exam_close_plan"),
                "帮我关闭 99 号考试",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("exam_session_close_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("exam.session.close", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(ExamSessionClosePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=关闭考试安排"));
        assertTrue(result.content().contains("二次确认文本=" + ExamSessionClosePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void examAnswerGradePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("exam_manual_grade_plan"),
                "帮我批改 2001 号答卷里的 3001 号主观题，给 8 分",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("exam_answer_grade_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("exam.answer.grade", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(ExamAnswerGradePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=批改考试答题"));
        assertTrue(result.content().contains("二次确认文本=" + ExamAnswerGradePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void gradeRecordCreatePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("grade_create_plan"),
                "给学生 30 录入 Java 期末成绩 95 分",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("grade_record_create_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("grade.record.create", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(GradeRecordCreatePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=录入成绩记录"));
        assertTrue(result.content().contains("二次确认文本=" + GradeRecordCreatePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void gradeRecordDeletePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("grade_delete_plan"),
                "删除 1001 号成绩记录",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("grade_record_delete_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("grade.record.delete", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(GradeRecordDeletePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=删除成绩记录"));
        assertTrue(result.content().contains("二次确认文本=" + GradeRecordDeletePlanTool.CONFIRMATION_TEXT));
    }

    @Test
    void gradeRecordUpdatePlanReturnsHighRiskWritePlan() {
        AgentToolExecutor service = newService();
        loginAsStudent(10);

        List<AgentToolResult> results = service.executeTools(
                List.of("grade_update_plan"),
                "把 1001 号成绩改成 95 分",
                5
        );

        assertEquals(1, results.size());
        AgentToolResult result = results.get(0);
        assertEquals("grade_record_update_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(result.success());
        assertEquals("grade.record.update", result.data().get("actionCode"));
        assertEquals("HIGH", result.data().get("riskLevel"));
        assertEquals(GradeRecordUpdatePlanTool.CONFIRMATION_TEXT, result.data().get("confirmationText"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.content().contains("操作计划=修改成绩记录"));
        assertTrue(result.content().contains("二次确认文本=" + GradeRecordUpdatePlanTool.CONFIRMATION_TEXT));
    }

    private AgentToolExecutor newService() {
        AgentToolSupport support = new AgentToolSupport(homeworkSubmitMapper, learningProgressMapper,
                chapterMapper, lessonMapper, courseMapper, teachingClassMapper, classStudentMapper);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        return new AgentToolExecutor(List.of(
                new CourseSearchTool(courseMapper, support),
                new ChapterSearchTool(chapterMapper, support),
                new LessonSearchTool(lessonMapper, support),
                new CourseResourceSearchTool(lessonMapper, support),
                new TeachingClassSearchTool(teachingClassMapper, support),
                new HomeworkSearchTool(homeworkMapper, support),
                new LearningProgressTool(support),
                new LearningPlanTool(support),
                new NoticeCreatePlanTool(pendingActionStore),
                new HomeworkCreatePlanTool(pendingActionStore),
                new HomeworkUpdatePlanTool(pendingActionStore),
                new HomeworkDeletePlanTool(pendingActionStore),
                new HomeworkSubmissionGradePlanTool(pendingActionStore),
                new ExamSessionCreatePlanTool(pendingActionStore),
                new ExamSessionClosePlanTool(pendingActionStore),
                new ExamAnswerGradePlanTool(pendingActionStore),
                new GradeRecordCreatePlanTool(pendingActionStore),
                new GradeRecordDeletePlanTool(pendingActionStore),
                new GradeRecordUpdatePlanTool(pendingActionStore)
        ));
    }

    private void loginAsStudent(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("student" + userId);
        user.setRoleCode("student");
        UserContext.set(user);
    }

    private ClassStudent classStudent(Integer classId) {
        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(classId);
        classStudent.setStudentId(10);
        classStudent.setStatus(1);
        return classStudent;
    }

    private TeachingClass teachingClass(Integer classId, Integer courseId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(classId);
        teachingClass.setCourseId(courseId);
        teachingClass.setStatus(1);
        return teachingClass;
    }

    private Chapter chapter(Integer id, Integer courseId, String title) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        chapter.setTitle(title);
        chapter.setDescription("章节说明");
        chapter.setSortOrder(1);
        return chapter;
    }

    private Course course(Integer id, String title) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setTeacher("张老师");
        course.setDifficulty("入门");
        course.setDescription("Java 课程基础内容");
        course.setStatus(1);
        return course;
    }

    private LearningProgress progress(Integer lessonId, Integer progress) {
        LearningProgress learningProgress = new LearningProgress();
        learningProgress.setStudentId(10);
        learningProgress.setLessonId(lessonId);
        learningProgress.setProgress(progress);
        learningProgress.setLearnedSeconds(progress * 60);
        learningProgress.setLastLearnTime(LocalDateTime.now());
        return learningProgress;
    }

    private Lesson lesson(Integer id, String title) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTitle(title);
        return lesson;
    }
}
