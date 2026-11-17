package com.cupk.aiacademy.analysis;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:learning-reports;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:contest-demo-schema.sql",
        "learning-analysis.worker-enabled=false"
})
class LearningAnalysisReportTest {
    @Autowired LearningAnalysisSnapshotService snapshots;
    @Autowired LearningAnalysisReportService reports;
    @Autowired LearningAnalysisTemplateService templates;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired com.cupk.aiacademy.agent.tool.AgentToolExecutor tools;
    @Autowired com.cupk.aiacademy.agent.core.service.AgentChatService agentChat;
    @Autowired com.cupk.aiacademy.assistant.UnifiedAssistantService assistant;
    @MockBean ChatModelClient model;
    @MockBean com.cupk.aiacademy.agent.planner.UnifiedAgentPlanner planner;

    @BeforeEach void setup() throws Exception {
        try (var connection = db.getDataSource().getConnection()) {
            for (String script : List.of("contest-demo-seed.sql", "learning-analysis-demo-seed.sql"))
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
        }
        db.update("DELETE FROM learning_analysis_report");
        teacher(1003901);
        when(model.configuredModel()).thenReturn("offline-test-model");
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void snapshotCoversBothClassesAndKeepsUnscoredStates() {
        var snapshot = snapshots.capture(1004911);
        assertEquals(2, snapshot.classes().size());
        assertEquals(16, snapshot.studentCount());
        assertEquals(6, snapshot.assessments().stream().filter(a -> a.type().equals("EXAM")).count());
        assertTrue(snapshot.homework().pendingCount() > 0);
        assertTrue(snapshot.homework().notStartedCount() > 0);
        assertTrue(snapshot.homework().inProgressCount() > 0);
        assertTrue(snapshot.assessments().stream().flatMap(a -> a.students().stream())
                .filter(s -> !s.status().equals("GRADED")).allMatch(s -> s.percentage() == null));
    }

    @Test void emptyAssessmentsKeepMissingMetricsNull() {
        db.update("INSERT INTO t_course(id,title,category_id,teacher_id,status) VALUES(1800003,'空评测课程',5,1003901,1)");
        var snapshot = snapshots.capture(1800003);
        db.update("DELETE FROM t_course WHERE id=1800003");
        assertEquals(0, snapshot.exam().assessmentCount());
        assertEquals(0, snapshot.homework().assessmentCount());
        assertNull(snapshot.exam().averageScore());
        assertNull(snapshot.exam().passRate());
        assertNull(snapshot.homework().submissionRate());
    }

    @Test void courseStudentCountDeduplicatesAcrossClasses() {
        db.update("INSERT INTO t_class_student(id,class_id,student_id,status) VALUES(1800002,1004932,1003902,1)");
        try {
            var snapshot = snapshots.capture(1004911);
            assertEquals(16, snapshot.studentCount());
            assertEquals(17, snapshot.classes().stream().mapToInt(LearningAnalysisSnapshot.ClassSummary::studentCount).sum());
        } finally {
            db.update("DELETE FROM t_class_student WHERE id=1800002");
        }
    }

    @Test void aggregationIsNotCappedAtFiftyAndNormalizesFullScores() {
        for (int i = 0; i < 60; i++) {
            int id = 1900000 + i;
            db.update("INSERT INTO t_exam_session(id,class_id,paper_id,teacher_id,title,status) VALUES(?,1004931,1004941,1003901,'全量统计测试',4)", id);
            db.update("INSERT INTO t_exam_record(exam_session_id,student_id,total_score,status) VALUES(?,1003902,40,2)", id);
        }
        db.update("UPDATE t_exam_paper SET total_score=50 WHERE id=1004941");
        try {
            var snapshot = snapshots.capture(1004911);
            assertTrue(snapshot.exam().gradedCount() > 50);
            var assessment = snapshot.assessments().stream().filter(a -> a.id() == 1900000).findFirst().orElseThrow();
            assertEquals(80.0, assessment.stats().averageScore());
        } finally {
            db.update("DELETE FROM t_exam_record WHERE exam_session_id>=1900000");
            db.update("DELETE FROM t_exam_session WHERE id>=1900000");
            db.update("UPDATE t_exam_paper SET total_score=100 WHERE id=1004941");
        }
    }

    @Test void unmigratedLegacyHomeworkIsIncludedOnce() {
        int before = snapshots.capture(1004911).homework().assessmentCount();
        db.update("INSERT INTO t_homework(id,class_id,teacher_id,title,full_score,status) VALUES(1800001,1004931,1003901,'旧版作业',50,1)");
        db.update("INSERT INTO t_homework_submit(id,homework_id,student_id,score,grade_status) VALUES(1800001,1800001,1003902,40,1)");
        try {
            var snapshot = snapshots.capture(1004911);
            assertEquals(before + 1, snapshot.homework().assessmentCount());
            assertEquals(80.0, snapshot.assessments().stream().filter(a -> a.type().equals("LEGACY_HOMEWORK")).findFirst().orElseThrow().stats().averageScore());
            db.update("INSERT INTO t_homework_assessment_migration(legacy_homework_id,homework_session_id,status) VALUES(1800001,1004981,'SUCCESS')");
            assertEquals(before, snapshots.capture(1004911).homework().assessmentCount());
        } finally {
            db.update("DELETE FROM t_homework_assessment_migration WHERE legacy_homework_id=1800001");
            db.update("DELETE FROM t_homework_submit WHERE id=1800001");
            db.update("DELETE FROM t_homework WHERE id=1800001");
        }
    }

    @Test void duplicateRequestReturnsSameReportAndExportsFrozenResult() throws Exception {
        when(model.call(any(Prompt.class), any(Duration.class))).thenReturn(validResponse());
        var request = new LearningAnalysisReportRequest(1004911, "default", 1000, "same-request");
        var report = reports.create(request);
        assertEquals(report.id(), reports.create(request).id());
        assertTrue(reports.runNext());
        var complete = reports.detail(report.id());
        assertEquals("SUCCESS", complete.status());
        assertTrue(complete.wordCount() >= 900 && complete.wordCount() <= 1100);
        assertEquals("offline-test-model", complete.model());
        byte[] word = reports.word(report.id());
        try (var doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a,b) -> a + b);
            assertFalse(text.contains("{{"));
            assertTrue(text.contains("学情演示"));
        }
        String csv = new String(reports.csv(report.id()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("SUBMITTED"));
        assertTrue(csv.contains("NOT_STARTED"));
        java.nio.file.Path output = java.nio.file.Path.of("target/learning-analysis-qa");
        java.nio.file.Files.createDirectories(output);
        java.nio.file.Files.write(output.resolve("default-template.docx"), templates.bytes("default"));
        java.nio.file.Files.write(output.resolve("offline-report.docx"), word);
    }

    @Test void badLengthGetsOneRepairAndFailureRemainsVisible() {
        when(model.call(any(Prompt.class), any(Duration.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));
        var report = reports.create(new LearningAnalysisReportRequest(1004911, "default", 1000, "repair"));
        assertTrue(reports.runNext());
        assertEquals("FAILED", reports.detail(report.id()).status());
        verify(model, times(2)).call(any(Prompt.class), any(Duration.class));
        assertThrows(RuntimeException.class, () -> reports.word(report.id()));
    }

    @Test void templatesReplaceSplitRunsAndRejectMissingMarkers() throws Exception {
        byte[] data;
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var p = doc.createParagraph();
            p.createRun().setText("{{course_"); p.createRun().setText("overview}}");
            doc.createTable(1,1).getRow(0).getCell(0).setText("{{course_name}}");
            doc.write(out); data = out.toByteArray();
        }
        var uploaded = templates.upload("split.docx", data);
        byte[] result = templates.render(templates.bytes(uploaded.id()), Map.of("course_overview", "填写正文", "course_name", "演示课程"));
        try (var doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertEquals("填写正文", doc.getParagraphs().getFirst().getText());
            assertEquals("演示课程", doc.getTables().getFirst().getText().trim());
        }
        assertThrows(RuntimeException.class, () -> templates.upload("bad.docx", new byte[]{1,2,3}));
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("没有正文填写位置");
            doc.write(out);
            assertThrows(RuntimeException.class, () -> templates.upload("missing.docx", out.toByteArray()));
        }
    }

    @Test void anotherTeacherAndStudentCannotReadCourseOrReport() {
        var report = reports.create(new LearningAnalysisReportRequest(1004911, "default", 1000, "private"));
        teacher(2);
        assertThrows(RuntimeException.class, () -> snapshots.capture(1004911));
        assertThrows(RuntimeException.class, () -> reports.detail(report.id()));
        var student = new AuthUtils.LoginUser(); student.setUserId(1003902); student.setRoleCode("student"); UserContext.set(student);
        assertThrows(RuntimeException.class, () -> reports.create(new LearningAnalysisReportRequest(1004911,"default",1000,"student")));
    }

    @Test void agentSelectedToolCreatesAReportWithoutGenericAnswerRewriting() {
        var tool = tools.executeReadTool("learning_analysis_report", Map.of("courseId", 1004911)).orElseThrow();
        when(planner.plan(any(), any())).thenReturn(com.cupk.aiacademy.agent.planner.AgentPlanningOutcome.readResult(tool));
        var createSession = new com.cupk.aiacademy.dto.AgentSessionCreateRequest();
        createSession.setAgentId(1); createSession.setTitle("学情报告测试");
        var session = agentChat.createSession(createSession);
        var message = new com.cupk.aiacademy.dto.AgentChatRequest();
        message.setContent("请生成课程1004911的学情报告");
        var response = agentChat.chat(session.getId(), message);
        assertNotNull(response.getReportId());
        assertEquals("QUEUED", response.getReportStatus());
        assertTrue(response.getContent().contains(response.getReportId()));
        assertEquals(response.getReportId(), agentChat.listMessages(session.getId()).getLast().getReportId());
        verify(model, never()).call(any(Prompt.class), any(Duration.class));
    }

    @Test void latestUnifiedAssistantCreatesAndRestoresTheReportReference() throws Exception {
        when(planner.plan(any(), any())).thenAnswer(invocation ->
                com.cupk.aiacademy.agent.planner.AgentPlanningOutcome.readResult(
                        tools.executeReadTool("learning_analysis_report", Map.of("courseId",1004911)).orElseThrow()));
        int session = ((Number) assistant.createSession(Map.of("courseId",1004911,"title","新版学情报告")).get("id")).intValue();
        assistant.send(session, Map.of("requestId","report-port-test","content","生成课程学情分析报告"));
        Map<String,Object> state = Map.of();
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < deadline) {
            state = assistant.request(session,"report-port-test");
            if (!"RUNNING".equals(state.get("status"))) break;
            Thread.sleep(30);
        }
        assertEquals("COMPLETED",state.get("status"),state.toString());
        var message = assistant.messages(session).getLast();
        assertNotNull(message.get("reportId"));
        assertEquals("QUEUED",reports.detail(message.get("reportId").toString()).status());
        assertTrue(message.get("content").toString().contains(message.get("reportId").toString()));
        verify(model,never()).call(any(Prompt.class),any(Duration.class));
    }

    @Test void latestCourseScopeRejectsCrossCourseReports() {
        db.update("INSERT INTO t_course(id,title,category_id,teacher_id,status) VALUES(1800004,'另一门自有课程',5,1003901,1)");
        try {
            assertThrows(RuntimeException.class, () ->
                    com.cupk.aiacademy.agent.tool.support.AgentToolScope.inCourse(1004911,
                            () -> tools.executeReadTool("learning_analysis_report", Map.of("courseId",1800004))));
        } finally {
            db.update("DELETE FROM t_course WHERE id=1800004");
        }
    }

    @Test void sidebarAnalysisPersistsPageSelectionWithoutCreatingReport() throws Exception {
        var action=Map.<String,Object>of("type","learning-analysis","courseId",1004911,"courseTitle","机器学习演示课程");
        when(planner.planForPage(any(),anyString())).thenReturn(
                com.cupk.aiacademy.agent.planner.AgentPlanningOutcome.page("请在页面点击生成。",action));
        assertPageHandoff(action,"分析机器学习演示课程学情");
    }

    @Test void sidebarCourseCreationPersistsPartialFieldsWithoutWritingCourse() throws Exception {
        var action=Map.<String,Object>of("type","course-create","fields",Map.of("title","人工智能基础"));
        when(planner.planForPage(any(),anyString())).thenReturn(
                com.cupk.aiacademy.agent.planner.AgentPlanningOutcome.page("请补全页面后点击下一步。",action));
        assertPageHandoff(action,"新建一门叫人工智能基础的课");
    }

    private void assertPageHandoff(Map<String,Object> action,String content) throws Exception {
        int courses=db.queryForObject("SELECT COUNT(*) FROM t_course",Integer.class);
        int reportsBefore=db.queryForObject("SELECT COUNT(*) FROM learning_analysis_report",Integer.class);
        int pending=db.queryForObject("SELECT COUNT(*) FROM t_agent_pending_action",Integer.class);
        int session=((Number)assistant.createSession(Map.of("title","页面准备测试")).get("id")).intValue();
        String requestId=UUID.randomUUID().toString();
        assistant.send(session,Map.of("requestId",requestId,"content",content,"context",Map.of("pageHandoff",true)));
        Map<String,Object> state=Map.of();
        long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
        while(System.nanoTime()<deadline) {
            state=assistant.request(session,requestId);
            if(!"RUNNING".equals(state.get("status")))break;
            Thread.sleep(30);
        }
        assertEquals("COMPLETED",state.get("status"),state.toString());
        var message=assistant.messages(session).getLast();
        assertEquals(action,message.get("pageAction"));
        assertNull(message.get("reportId"));
        assertNull(message.get("pendingAction"));
        assertEquals(courses,db.queryForObject("SELECT COUNT(*) FROM t_course",Integer.class));
        assertEquals(reportsBefore,db.queryForObject("SELECT COUNT(*) FROM learning_analysis_report",Integer.class));
        assertEquals(pending,db.queryForObject("SELECT COUNT(*) FROM t_agent_pending_action",Integer.class));
        verify(planner).planForPage(any(),eq(content));
    }

    private ChatResponse validResponse() throws Exception {
        Map<String,String> sections = new LinkedHashMap<>();
        for (String field : LearningAnalysisTemplateService.SECTIONS.keySet()) sections.put(field, "学".repeat(155));
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json.writeValueAsString(sections)))));
    }
    private void teacher(int id) {
        var actor = new AuthUtils.LoginUser(); actor.setUserId(id); actor.setRoleCode("teacher"); UserContext.set(actor);
    }
}
