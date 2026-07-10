package com.cupk.aiacademy.agent.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cupk.aiacademy.agent.action.course.ChapterDeleteActionExecutor;
import com.cupk.aiacademy.agent.action.exam.ExamAnswerGradeActionExecutor;
import com.cupk.aiacademy.agent.action.exam.QuestionCreateActionExecutor;
import com.cupk.aiacademy.agent.action.exam.QuestionDeleteActionExecutor;
import com.cupk.aiacademy.agent.action.exam.QuestionUpdateActionExecutor;
import com.cupk.aiacademy.agent.action.exam.ExamSessionCloseActionExecutor;
import com.cupk.aiacademy.agent.action.exam.ExamSessionCreateActionExecutor;
import com.cupk.aiacademy.agent.tool.course.ChapterDeletePlanTool;
import com.cupk.aiacademy.agent.action.grade.GradeRecordCreateActionExecutor;
import com.cupk.aiacademy.agent.action.grade.GradeRecordDeleteActionExecutor;
import com.cupk.aiacademy.agent.action.grade.GradeRecordUpdateActionExecutor;
import com.cupk.aiacademy.agent.action.homework.HomeworkCreateActionExecutor;
import com.cupk.aiacademy.agent.action.homework.HomeworkDeleteActionExecutor;
import com.cupk.aiacademy.agent.action.homework.HomeworkSubmissionGradeActionExecutor;
import com.cupk.aiacademy.agent.action.homework.HomeworkUpdateActionExecutor;
import com.cupk.aiacademy.agent.action.notice.NoticeCreateActionExecutor;
import com.cupk.aiacademy.agent.tool.exam.ExamAnswerGradePlanTool;
import com.cupk.aiacademy.agent.tool.exam.ExamSessionClosePlanTool;
import com.cupk.aiacademy.agent.tool.exam.ExamSessionCreatePlanTool;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordCreatePlanTool;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordDeletePlanTool;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordUpdatePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkDeletePlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkSubmissionGradePlanTool;
import com.cupk.aiacademy.agent.tool.exam.QuestionDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.common.SubmissionStatus;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.GradeRecord;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.OperationLog;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.service.ExamService;
import com.cupk.aiacademy.service.QuestionService;
import com.cupk.aiacademy.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentActionExecutionServiceTest {
    @Mock
    private NoticeMapper noticeMapper;

    @Mock
    private HomeworkMapper homeworkMapper;

    @Mock
    private HomeworkSubmitMapper homeworkSubmitMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private ExamService examService;

    @Mock
    private GradeRecordMapper gradeRecordMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private ExamSessionMapper examSessionMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private QuestionService questionService;

    @Mock
    private ExamPaperQuestionMapper examPaperQuestionMapper;

    @Mock
    private ExamAnswerMapper examAnswerMapper;

    private final InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void confirmNoticeCreateInsertsNoticeAndAuditLog() {
        loginAs("teacher", 7);
        when(userMapper.selectById(7)).thenReturn(new User());
        when(teachingClassMapper.selectById(3)).thenReturn(new TeachingClass());
        when(noticeMapper.insert(any(Notice.class))).thenAnswer(invocation -> {
            Notice notice = invocation.getArgument(0);
            notice.setId(55);
            return 1;
        });
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "notice.create",
                true,
                Map.of(
                        "title", "作业提醒",
                        "content", "明天提交第一章作业",
                        "classId", 3,
                        "noticeType", "homework"
                )
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("notice.create", result.actionCode());
        assertEquals(55, result.data().get("noticeId"));

        ArgumentCaptor<Notice> noticeCaptor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(noticeCaptor.capture());
        Notice notice = noticeCaptor.getValue();
        assertEquals(7, notice.getPublisherId());
        assertEquals(3, notice.getClassId());
        assertEquals("作业提醒", notice.getTitle());
        assertEquals("明天提交第一章作业", notice.getContent());
        assertEquals("homework", notice.getNoticeType());
        assertEquals(1, notice.getStatus());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.notice.create", log.getOperation());
        assertEquals("POST", log.getMethod());
        assertEquals("/api/agent/actions/confirm", log.getUrl());
        assertEquals("127.0.0.1", log.getIp());
        assertTrue(log.getParams().contains("\"actionCode\":\"notice.create\""));
        assertTrue(log.getParams().contains("\"noticeId\":55"));
    }

    @Test
    void confirmCanUsePendingActionIdAndConsumesItAfterSuccess() {
        loginAs("teacher", 7);
        when(userMapper.selectById(7)).thenReturn(new User());
        when(noticeMapper.insert(any(Notice.class))).thenAnswer(invocation -> {
            Notice notice = invocation.getArgument(0);
            notice.setId(56);
            return 1;
        });
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentPendingAction pendingAction = pendingActionStore.create(
                "notice.create",
                7,
                Map.of("rawRequest", "提醒一班提交作业"),
                Duration.ofMinutes(10)
        );
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                null,
                true,
                Map.of(
                        "title", "作业提醒",
                        "content", "明天提交第一章作业"
                ),
                pendingAction.actionId()
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals(56, result.data().get("noticeId"));
        assertTrue(pendingActionStore.findById(pendingAction.actionId()).isEmpty());
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertTrue(logCaptor.getValue().getParams().contains("\"actionId\":\"" + pendingAction.actionId() + "\""));
    }

    @Test
    void confirmRequiresExplicitConfirmation() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("notice.create", false, Map.of()),
                ""
        ));

        assertEquals("写操作必须由用户确认后才能执行", exception.getMessage());
        verify(noticeMapper, never()).insert(any(Notice.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void noticeCreateRejectsStudentRole() {
        loginAs("student", 9);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("notice.create", true, Map.of(
                        "title", "作业提醒",
                        "content", "明天提交作业"
                )),
                ""
        ));

        assertEquals("只有教师或管理员可以发布通知", exception.getMessage());
        verify(noticeMapper, never()).insert(any(Notice.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void noticeCreateRequiresContent() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("notice.create", true, Map.of("title", "作业提醒")),
                ""
        ));

        assertEquals("content 不能为空", exception.getMessage());
        verify(noticeMapper, never()).insert(any(Notice.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmHomeworkCreateInsertsHomeworkAndAuditLog() {
        loginAs("teacher", 7);
        when(teachingClassMapper.selectById(3)).thenReturn(new TeachingClass());
        when(userMapper.selectById(7)).thenReturn(new User());
        when(homeworkMapper.insert(any(Homework.class))).thenAnswer(invocation -> {
            Homework homework = invocation.getArgument(0);
            homework.setId(88);
            return 1;
        });
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "homework.create",
                true,
                Map.of(
                        "classId", 3,
                        "title", "第一章作业",
                        "content", "完成课后练习",
                        "fullScore", "100",
                        "deadline", "2026-07-06 18:00:00"
                )
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("homework.create", result.actionCode());
        assertEquals(88, result.data().get("homeworkId"));

        ArgumentCaptor<Homework> homeworkCaptor = ArgumentCaptor.forClass(Homework.class);
        verify(homeworkMapper).insert(homeworkCaptor.capture());
        Homework homework = homeworkCaptor.getValue();
        assertEquals(3, homework.getClassId());
        assertEquals(7, homework.getTeacherId());
        assertEquals("第一章作业", homework.getTitle());
        assertEquals("完成课后练习", homework.getContent());
        assertEquals(0, homework.getFullScore().compareTo(new java.math.BigDecimal("100")));
        assertEquals(LocalDateTime.of(2026, 7, 6, 18, 0), homework.getDeadline());
        assertEquals(1, homework.getStatus());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.homework.create", log.getOperation());
        assertTrue(log.getParams().contains("\"homeworkId\":88"));
    }

    @Test
    void homeworkCreateRejectsStudentRole() {
        loginAs("student", 9);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("homework.create", true, Map.of(
                        "classId", 3,
                        "title", "第一章作业"
                )),
                ""
        ));

        assertEquals("只有教师或管理员可以发布作业", exception.getMessage());
        verify(homeworkMapper, never()).insert(any(Homework.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void homeworkCreateRejectsInvalidTimeRange() {
        loginAs("teacher", 7);
        when(teachingClassMapper.selectById(3)).thenReturn(new TeachingClass());
        when(userMapper.selectById(7)).thenReturn(new User());
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("homework.create", true, Map.of(
                        "classId", 3,
                        "title", "第一章作业",
                        "startTime", "2026-07-06T18:00:00",
                        "deadline", "2026-07-06T18:00:00"
                )),
                ""
        ));

        assertEquals("截止时间必须晚于开始时间", exception.getMessage());
        verify(homeworkMapper, never()).insert(any(Homework.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmHomeworkUpdateUpdatesHomeworkAndAuditLog() {
        loginAs("teacher", 7);
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 7));
        when(homeworkMapper.updateById(any(Homework.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "homework.update",
                true,
                Map.of(
                        "homeworkId", "88",
                        "title", "第一章作业修订版",
                        "content", "补充第三题说明",
                        "fullScore", "120",
                        "deadline", "2026-07-07T18:00:00",
                        "status", 1
                )
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("homework.update", result.actionCode());
        assertEquals(88, result.data().get("homeworkId"));
        assertEquals("第一章作业", result.data().get("oldTitle"));
        assertEquals("第一章作业修订版", result.data().get("newTitle"));

        ArgumentCaptor<Homework> homeworkCaptor = ArgumentCaptor.forClass(Homework.class);
        verify(homeworkMapper).updateById(homeworkCaptor.capture());
        Homework homework = homeworkCaptor.getValue();
        assertEquals(88, homework.getId());
        assertEquals(7, homework.getTeacherId());
        assertEquals("第一章作业修订版", homework.getTitle());
        assertEquals("补充第三题说明", homework.getContent());
        assertEquals(new BigDecimal("120"), homework.getFullScore());
        assertEquals(LocalDateTime.parse("2026-07-07T18:00:00"), homework.getDeadline());
        assertEquals(1, homework.getStatus());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.homework.update", log.getOperation());
        assertTrue(log.getParams().contains("\"actionCode\":\"homework.update\""));
        assertTrue(log.getParams().contains("\"homeworkId\":\"88\""));
    }

    @Test
    void homeworkUpdateRejectsTeacherForOtherTeacherHomework() {
        loginAs("teacher", 7);
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("homework.update", true, Map.of(
                        "homeworkId", 88,
                        "title", "第一章作业修订版"
                )),
                ""
        ));

        assertEquals("只能修改当前登录教师发布的作业", exception.getMessage());
        verify(homeworkMapper, never()).updateById(any(Homework.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void homeworkUpdateRequiresMutableField() {
        loginAs("teacher", 7);
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 7));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("homework.update", true, Map.of("homeworkId", 88)),
                ""
        ));

        assertEquals("至少需要提供一个可修改的作业字段", exception.getMessage());
        verify(homeworkMapper, never()).updateById(any(Homework.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void homeworkDeleteRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("homework.delete", true, Map.of("homeworkId", 88)),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + HomeworkDeletePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verify(homeworkMapper, never()).deleteById(88);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmHomeworkDeleteDeletesHomeworkAndAuditLog() {
        loginAs("teacher", 7);
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 7));
        when(homeworkMapper.deleteById(88)).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "homework.delete",
                true,
                Map.of("homeworkId", "88"),
                null,
                HomeworkDeletePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("homework.delete", result.actionCode());
        assertEquals(88, result.data().get("homeworkId"));
        assertEquals("第一章作业", result.data().get("title"));

        verify(homeworkMapper).deleteById(88);
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.homework.delete", log.getOperation());
        assertTrue(log.getParams().contains("\"actionCode\":\"homework.delete\""));
        assertTrue(log.getParams().contains("\"homeworkId\":\"88\""));
    }

    @Test
    void homeworkDeleteRejectsTeacherForOtherTeacherHomework() {
        loginAs("teacher", 7);
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "homework.delete",
                        true,
                        Map.of("homeworkId", 88),
                        null,
                        HomeworkDeletePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能删除当前登录教师发布的作业", exception.getMessage());
        verify(homeworkMapper, never()).deleteById(88);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void homeworkSubmissionGradeRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("homework.submission.grade", true, Map.of(
                        "homeworkSubmitId", 501,
                        "score", 92
                )),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verify(homeworkSubmitMapper, never()).updateById(any(HomeworkSubmit.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmHomeworkSubmissionGradeUpdatesSubmitAndAuditLog() {
        loginAs("teacher", 7);
        when(homeworkSubmitMapper.selectById(501)).thenReturn(homeworkSubmit(501, 88));
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 7));
        when(homeworkSubmitMapper.updateById(any(HomeworkSubmit.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "homework.submission.grade",
                true,
                Map.of(
                        "homeworkSubmitId", "501",
                        "score", "92",
                        "feedback", "完成质量较高"
                ),
                null,
                HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("homework.submission.grade", result.actionCode());
        assertEquals(501, result.data().get("homeworkSubmitId"));
        assertEquals(new BigDecimal("80"), result.data().get("oldScore"));
        assertEquals(new BigDecimal("92"), result.data().get("newScore"));

        ArgumentCaptor<HomeworkSubmit> submitCaptor = ArgumentCaptor.forClass(HomeworkSubmit.class);
        verify(homeworkSubmitMapper).updateById(submitCaptor.capture());
        HomeworkSubmit submit = submitCaptor.getValue();
        assertEquals(501, submit.getId());
        assertEquals(88, submit.getHomeworkId());
        assertEquals(new BigDecimal("92"), submit.getScore());
        assertEquals("完成质量较高", submit.getFeedback());
        assertEquals(1, submit.getGradeStatus());
        assertEquals(7, submit.getGraderId());
        assertTrue(submit.getGradeTime() != null);

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.homework.submission.grade", log.getOperation());
        assertTrue(log.getParams().contains("\"actionCode\":\"homework.submission.grade\""));
        assertTrue(log.getParams().contains("\"homeworkSubmitId\":\"501\""));
    }

    @Test
    void homeworkSubmissionGradeRejectsTeacherForOtherTeacherHomework() {
        loginAs("teacher", 7);
        when(homeworkSubmitMapper.selectById(501)).thenReturn(homeworkSubmit(501, 88));
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "homework.submission.grade",
                        true,
                        Map.of("homeworkSubmitId", 501, "score", 92),
                        null,
                        HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能批改当前登录教师发布作业的提交", exception.getMessage());
        verify(homeworkSubmitMapper, never()).updateById(any(HomeworkSubmit.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void homeworkSubmissionGradeRejectsScoreOverFullScore() {
        loginAs("teacher", 7);
        when(homeworkSubmitMapper.selectById(501)).thenReturn(homeworkSubmit(501, 88));
        when(homeworkMapper.selectById(88)).thenReturn(homework(88, 7));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "homework.submission.grade",
                        true,
                        Map.of("homeworkSubmitId", 501, "score", 120),
                        null,
                        HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("分数不能超过作业满分", exception.getMessage());
        verify(homeworkSubmitMapper, never()).updateById(any(HomeworkSubmit.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void examSessionCreateRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("exam.session.create", true, Map.of(
                        "classId", 3,
                        "paperId", 5,
                        "name", "Java 期末考试",
                        "startTime", "2026-07-06T09:00:00",
                        "endTime", "2026-07-06T11:00:00",
                        "durationMinutes", 120
                )),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + ExamSessionCreatePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verifyNoInteractions(examService);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmExamSessionCreateCallsExamServiceAndAuditLog() {
        loginAs("teacher", 7);
        when(teachingClassMapper.selectById(3)).thenReturn(new TeachingClass());
        when(userMapper.selectById(7)).thenReturn(new User());
        when(examService.create(any(ExamRequestDTO.class))).thenReturn(examResponse());
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "exam.session.create",
                true,
                Map.of(
                        "classId", 3,
                        "paperId", 5,
                        "name", "Java 期末考试",
                        "description", "闭卷考试",
                        "startTime", "2026-07-06T09:00:00",
                        "endTime", "2026-07-06T11:00:00",
                        "durationMinutes", 120,
                        "status", "PUBLISHED"
                ),
                null,
                ExamSessionCreatePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("exam.session.create", result.actionCode());
        assertEquals(99L, result.data().get("examSessionId"));

        ArgumentCaptor<ExamRequestDTO> requestCaptor = ArgumentCaptor.forClass(ExamRequestDTO.class);
        verify(examService).create(requestCaptor.capture());
        ExamRequestDTO request = requestCaptor.getValue();
        assertEquals(3L, request.getClassId());
        assertEquals(7L, request.getTeacherId());
        assertEquals(5L, request.getPaperId());
        assertEquals("Java 期末考试", request.getName());
        assertEquals(120, request.getDurationMinutes());
        assertEquals(ExamStatus.PUBLISHED, request.getStatus());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.exam.session.create", log.getOperation());
        assertTrue(log.getParams().contains("\"examSessionId\":99"));
    }

    @Test
    void examSessionCloseRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("exam.session.close", true, Map.of("examSessionId", 99)),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + ExamSessionClosePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verifyNoInteractions(examService);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmExamSessionCloseCallsExamServiceAndAuditLog() {
        loginAs("teacher", 7);
        when(examService.getById(99L)).thenReturn(examResponse());
        when(examService.close(99L)).thenReturn(closedExamResponse());
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "exam.session.close",
                true,
                Map.of("examSessionId", "99"),
                null,
                ExamSessionClosePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("exam.session.close", result.actionCode());
        assertEquals(99L, result.data().get("examSessionId"));
        assertEquals("CLOSED", result.data().get("status"));

        verify(examService).getById(99L);
        verify(examService).close(99L);
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.exam.session.close", log.getOperation());
        assertTrue(log.getParams().contains("\"examSessionId\":99"));
    }

    @Test
    void gradeRecordUpdateRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("grade.record.update", true, Map.of(
                        "gradeRecordId", 1001,
                        "score", 95
                )),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + GradeRecordUpdatePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verifyNoInteractions(gradeRecordMapper);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmGradeRecordUpdateUpdatesScoreAndAuditLog() {
        loginAs("teacher", 7);
        when(gradeRecordMapper.selectById(1001)).thenReturn(gradeRecord(1001, 7));
        when(gradeRecordMapper.updateById(any(GradeRecord.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "grade.record.update",
                true,
                Map.of(
                        "gradeRecordId", "1001",
                        "score", "95",
                        "fullScore", "100",
                        "comment", "复核后调整",
                        "gradeName", "期末总评"
                ),
                null,
                GradeRecordUpdatePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("grade.record.update", result.actionCode());
        assertEquals(1001, result.data().get("gradeRecordId"));
        assertEquals(new BigDecimal("80"), result.data().get("oldScore"));
        assertEquals(new BigDecimal("95"), result.data().get("newScore"));

        ArgumentCaptor<GradeRecord> gradeCaptor = ArgumentCaptor.forClass(GradeRecord.class);
        verify(gradeRecordMapper).updateById(gradeCaptor.capture());
        GradeRecord grade = gradeCaptor.getValue();
        assertEquals(1001, grade.getId());
        assertEquals(7, grade.getTeacherId());
        assertEquals(new BigDecimal("95"), grade.getScore());
        assertEquals(new BigDecimal("100"), grade.getFullScore());
        assertEquals("复核后调整", grade.getComment());
        assertEquals("期末总评", grade.getGradeName());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.grade.record.update", log.getOperation());
        assertTrue(log.getParams().contains("\"gradeRecordId\":1001"));
    }

    @Test
    void gradeRecordUpdateRejectsTeacherForOtherTeacherRecord() {
        loginAs("teacher", 7);
        when(gradeRecordMapper.selectById(1001)).thenReturn(gradeRecord(1001, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "grade.record.update",
                        true,
                        Map.of("gradeRecordId", 1001, "score", 95),
                        null,
                        GradeRecordUpdatePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能修改当前登录教师负责的成绩记录", exception.getMessage());
        verify(gradeRecordMapper, never()).updateById(any(GradeRecord.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void gradeRecordCreateRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("grade.record.create", true, Map.of(
                        "studentId", 30,
                        "classId", 3,
                        "courseId", 5,
                        "gradeName", "Java 期末成绩",
                        "score", 95
                )),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + GradeRecordCreatePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verify(gradeRecordMapper, never()).insert(any(GradeRecord.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmGradeRecordCreateInsertsGradeAndAuditLog() {
        loginAs("teacher", 7);
        when(userMapper.selectById(30)).thenReturn(new User());
        when(userMapper.selectById(7)).thenReturn(new User());
        when(teachingClassMapper.selectById(3)).thenReturn(new TeachingClass());
        when(courseMapper.selectById(5)).thenReturn(new Course());
        when(gradeRecordMapper.insert(any(GradeRecord.class))).thenAnswer(invocation -> {
            GradeRecord grade = invocation.getArgument(0);
            grade.setId(1002);
            return 1;
        });
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "grade.record.create",
                true,
                Map.of(
                        "studentId", "30",
                        "classId", "3",
                        "courseId", "5",
                        "gradeName", "Java 期末成绩",
                        "score", "95",
                        "fullScore", "100",
                        "comment", "自然语言录入"
                ),
                null,
                GradeRecordCreatePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("grade.record.create", result.actionCode());
        assertEquals(1002, result.data().get("gradeRecordId"));
        assertEquals(new BigDecimal("95"), result.data().get("score"));

        ArgumentCaptor<GradeRecord> gradeCaptor = ArgumentCaptor.forClass(GradeRecord.class);
        verify(gradeRecordMapper).insert(gradeCaptor.capture());
        GradeRecord grade = gradeCaptor.getValue();
        assertEquals(30, grade.getStudentId());
        assertEquals(3, grade.getClassId());
        assertEquals(5, grade.getCourseId());
        assertEquals(7, grade.getTeacherId());
        assertEquals("Java 期末成绩", grade.getGradeName());
        assertEquals("manual", grade.getSourceType());
        assertEquals(new BigDecimal("95"), grade.getScore());
        assertEquals(new BigDecimal("100"), grade.getFullScore());
        assertEquals("自然语言录入", grade.getComment());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.grade.record.create", log.getOperation());
        assertTrue(log.getParams().contains("\"gradeRecordId\":1002"));
    }

    @Test
    void gradeRecordCreateRejectsStudentRole() {
        loginAs("student", 30);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "grade.record.create",
                        true,
                        Map.of("studentId", 30, "classId", 3, "courseId", 5, "gradeName", "Java 期末成绩", "score", 95),
                        null,
                        GradeRecordCreatePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只有教师或管理员可以录入成绩", exception.getMessage());
        verify(gradeRecordMapper, never()).insert(any(GradeRecord.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void gradeRecordDeleteRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("grade.record.delete", true, Map.of("gradeRecordId", 1001)),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + GradeRecordDeletePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verify(gradeRecordMapper, never()).deleteById(1001);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmGradeRecordDeleteDeletesGradeAndAuditLog() {
        loginAs("teacher", 7);
        when(gradeRecordMapper.selectById(1001)).thenReturn(gradeRecord(1001, 7));
        when(gradeRecordMapper.deleteById(1001)).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "grade.record.delete",
                true,
                Map.of("gradeRecordId", "1001"),
                null,
                GradeRecordDeletePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("grade.record.delete", result.actionCode());
        assertEquals(1001, result.data().get("gradeRecordId"));
        assertEquals(30, result.data().get("studentId"));
        assertEquals(new BigDecimal("80"), result.data().get("score"));

        verify(gradeRecordMapper).deleteById(1001);
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.grade.record.delete", log.getOperation());
        assertTrue(log.getParams().contains("\"gradeRecordId\":1001"));
    }

    @Test
    void gradeRecordDeleteRejectsTeacherForOtherTeacherRecord() {
        loginAs("teacher", 7);
        when(gradeRecordMapper.selectById(1001)).thenReturn(gradeRecord(1001, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "grade.record.delete",
                        true,
                        Map.of("gradeRecordId", 1001),
                        null,
                        GradeRecordDeletePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能删除当前登录教师负责的成绩记录", exception.getMessage());
        verify(gradeRecordMapper, never()).deleteById(1001);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void examAnswerGradeRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("exam.answer.grade", true, Map.of(
                        "submissionId", 2001,
                        "submissionAnswerId", 3001,
                        "score", 8
                )),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + ExamAnswerGradePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verifyNoInteractions(submissionService);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmExamAnswerGradeCallsSubmissionServiceAndAuditLog() {
        loginAs("teacher", 7);
        when(submissionService.getEntityById(2001L)).thenReturn(examRecord(2001, 99));
        when(examSessionMapper.selectById(99)).thenReturn(examSession(99, 7));
        when(submissionService.manualGrade(org.mockito.ArgumentMatchers.eq(2001L), any(SubmissionRequestsDTO.ManualGradeRequest.class)))
                .thenReturn(submissionResponse());
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "exam.answer.grade",
                true,
                Map.of(
                        "submissionId", "2001",
                        "submissionAnswerId", "3001",
                        "score", "8.5",
                        "comment", "要点完整"
                ),
                null,
                ExamAnswerGradePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("exam.answer.grade", result.actionCode());
        assertEquals(2001L, result.data().get("submissionId"));
        assertEquals(new BigDecimal("28.5"), result.data().get("totalScore"));
        assertEquals(1, result.data().get("gradedCount"));

        ArgumentCaptor<SubmissionRequestsDTO.ManualGradeRequest> requestCaptor =
                ArgumentCaptor.forClass(SubmissionRequestsDTO.ManualGradeRequest.class);
        verify(submissionService).manualGrade(org.mockito.ArgumentMatchers.eq(2001L), requestCaptor.capture());
        SubmissionRequestsDTO.ManualGradeRequest request = requestCaptor.getValue();
        assertEquals(7L, request.getGraderId());
        assertEquals(1, request.getGrades().size());
        assertEquals(3001L, request.getGrades().get(0).getSubmissionAnswerId());
        assertEquals(new BigDecimal("8.5"), request.getGrades().get(0).getScore());
        assertEquals("要点完整", request.getGrades().get(0).getComment());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.exam.answer.grade", log.getOperation());
        assertTrue(log.getParams().contains("\"submissionId\":\"2001\""));
    }

    @Test
    void examAnswerGradeRejectsTeacherForOtherTeacherExam() {
        loginAs("teacher", 7);
        when(submissionService.getEntityById(2001L)).thenReturn(examRecord(2001, 99));
        when(examSessionMapper.selectById(99)).thenReturn(examSession(99, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "exam.answer.grade",
                        true,
                        Map.of("submissionId", 2001, "submissionAnswerId", 3001, "score", 8),
                        null,
                        ExamAnswerGradePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能批改当前登录教师负责的考试答卷", exception.getMessage());
        verify(submissionService, never()).manualGrade(any(), any());
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void chapterDeleteRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("chapter.delete", true, Map.of("chapterId", 11)),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + ChapterDeletePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verify(chapterMapper, never()).updateById(any(Chapter.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmChapterDeleteSoftDeletesChapterAndAuditLog() {
        loginAs("teacher", 7);
        when(chapterMapper.selectById(11)).thenReturn(chapter(11, 5));
        when(courseMapper.selectById(5)).thenReturn(course(5, 7));
        when(lessonMapper.selectCount(any())).thenReturn(2L);
        when(chapterMapper.updateById(any(Chapter.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "chapter.delete",
                true,
                Map.of("chapterId", "11"),
                null,
                ChapterDeletePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("chapter.delete", result.actionCode());
        assertEquals(11, result.data().get("chapterId"));
        assertEquals(true, result.data().get("softDeleted"));
        assertEquals(2, result.data().get("affectedLessonCount"));

        ArgumentCaptor<Chapter> chapterCaptor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper).updateById(chapterCaptor.capture());
        Chapter update = chapterCaptor.getValue();
        assertEquals(11, update.getId());
        assertEquals(Chapter.STATUS_DELETED, update.getStatus());
        assertTrue(update.getUpdatedAt() != null);

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.chapter.delete", log.getOperation());
        assertTrue(log.getParams().contains("\"chapterId\":\"11\""));
    }

    @Test
    void chapterDeleteRejectsTeacherForOtherTeacherCourse() {
        loginAs("teacher", 7);
        when(chapterMapper.selectById(11)).thenReturn(chapter(11, 5));
        when(courseMapper.selectById(5)).thenReturn(course(5, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "chapter.delete",
                        true,
                        Map.of("chapterId", 11),
                        null,
                        ChapterDeletePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能删除当前登录教师负责课程下的章节", exception.getMessage());
        verify(chapterMapper, never()).updateById(any(Chapter.class));
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmQuestionCreateCallsQuestionServiceAndAuditLog() {
        loginAs("teacher", 7);
        when(courseMapper.selectById(5)).thenReturn(course(5, 7));
        when(userMapper.selectById(7)).thenReturn(new User());
        when(questionService.create(any(QuestionRequestDTO.class))).thenReturn(questionResponse());
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "question.create",
                true,
                Map.of(
                        "courseId", 5,
                        "type", "single",
                        "content", "Java 中 int 类型默认值是多少？",
                        "options", List.of(
                                Map.of("optionKey", "A", "optionContent", "0", "sortOrder", 1),
                                Map.of("optionKey", "B", "optionContent", "null", "sortOrder", 2)
                        ),
                        "answers", List.of("A"),
                        "difficulty", "easy",
                        "score", "5",
                        "knowledgePoint", "Java基础"
                )
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("question.create", result.actionCode());
        assertEquals(3001L, result.data().get("questionId"));
        assertEquals(2, result.data().get("optionCount"));
        assertEquals(1, result.data().get("answerCount"));

        ArgumentCaptor<QuestionRequestDTO> requestCaptor = ArgumentCaptor.forClass(QuestionRequestDTO.class);
        verify(questionService).create(requestCaptor.capture());
        QuestionRequestDTO request = requestCaptor.getValue();
        assertEquals(5L, request.getCourseId());
        assertEquals(7L, request.getTeacherId());
        assertEquals(QuestionType.SINGLE_CHOICE, request.getType());
        assertEquals(Difficulty.EASY, request.getDifficulty());
        assertEquals(RecordStatus.ACTIVE, request.getStatus());
        assertEquals(2, request.getOptions().size());
        assertEquals(1, request.getAnswers().size());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertEquals("agent.question.create", logCaptor.getValue().getOperation());
    }

    @Test
    void questionCreateRejectsStudentRole() {
        loginAs("student", 30);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("question.create", true, Map.of(
                        "courseId", 5,
                        "type", "short_answer",
                        "content", "解释 JVM 内存模型",
                        "answers", List.of("方法区、堆、栈等")
                )),
                ""
        ));

        assertEquals("只有教师或管理员可以创建试题", exception.getMessage());
        verifyNoInteractions(questionService);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void questionCreateRejectsTeacherForOtherTeacherCourse() {
        loginAs("teacher", 7);
        when(courseMapper.selectById(5)).thenReturn(course(5, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("question.create", true, Map.of(
                        "courseId", 5,
                        "type", "short_answer",
                        "content", "解释 JVM 内存模型",
                        "answers", List.of("方法区、堆、栈等")
                )),
                ""
        ));

        assertEquals("题目教师必须是课程负责教师", exception.getMessage());
        verifyNoInteractions(questionService);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmQuestionUpdateMergesExistingQuestionAndAuditLog() {
        loginAs("teacher", 7);
        when(questionService.getById(3001L)).thenReturn(questionResponse(), updatedQuestionResponse());
        when(courseMapper.selectById(5)).thenReturn(course(5, 7));
        when(userMapper.selectById(7)).thenReturn(new User());
        when(questionService.update(org.mockito.ArgumentMatchers.eq(3001L), any(QuestionRequestDTO.class)))
                .thenReturn(updatedQuestionResponse());
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "question.update",
                true,
                Map.of(
                        "questionId", "3001",
                        "content", "Java 中 int 类型默认值是什么？",
                        "analysis", "int 是基本数据类型，默认值为 0。"
                )
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("question.update", result.actionCode());
        assertEquals(3001L, result.data().get("questionId"));
        assertEquals("Java 中 int 类型默认值是什么？", result.data().get("newContent"));

        ArgumentCaptor<QuestionRequestDTO> requestCaptor = ArgumentCaptor.forClass(QuestionRequestDTO.class);
        verify(questionService).update(org.mockito.ArgumentMatchers.eq(3001L), requestCaptor.capture());
        QuestionRequestDTO request = requestCaptor.getValue();
        assertEquals(5L, request.getCourseId());
        assertEquals(7L, request.getTeacherId());
        assertEquals(QuestionType.SINGLE_CHOICE, request.getType());
        assertEquals(Difficulty.EASY, request.getDifficulty());
        assertEquals("Java 中 int 类型默认值是什么？", request.getContent());
        assertEquals("int 是基本数据类型，默认值为 0。", request.getAnalysis());
        assertEquals(2, request.getOptions().size());
        assertEquals(1, request.getAnswers().size());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertEquals("agent.question.update", logCaptor.getValue().getOperation());
    }

    @Test
    void questionUpdateRequiresAtLeastOneField() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("question.update", true, Map.of("questionId", 3001)),
                ""
        ));

        assertEquals("至少需要提供一个可修改的试题字段", exception.getMessage());
        verifyNoInteractions(questionService);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void questionUpdateRejectsTeacherForOtherTeacherQuestion() {
        loginAs("teacher", 7);
        QuestionResponseDTO oldQuestion = questionResponse();
        oldQuestion.setTeacherId(8L);
        when(questionService.getById(3001L)).thenReturn(oldQuestion);
        when(courseMapper.selectById(5)).thenReturn(course(5, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("question.update", true, Map.of(
                        "questionId", 3001,
                        "content", "修改后的题干"
                )),
                ""
        ));

        assertEquals("只能修改当前登录教师负责的试题", exception.getMessage());
        verify(questionService, never()).update(any(), any());
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void questionDeleteRequiresHighRiskConfirmationText() {
        loginAs("teacher", 7);
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("question.delete", true, Map.of("questionId", 3001)),
                ""
        ));

        assertEquals("高风险操作需要输入确认文本：" + QuestionDeletePlanTool.CONFIRMATION_TEXT, exception.getMessage());
        verify(questionService, never()).delete(3001L);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void confirmQuestionDeleteDeletesQuestionAndAuditLog() {
        loginAs("teacher", 7);
        when(questionService.getById(3001L)).thenReturn(questionResponse());
        when(courseMapper.selectById(5)).thenReturn(course(5, 7));
        when(examPaperQuestionMapper.selectCount(any())).thenReturn(2L);
        when(examAnswerMapper.selectCount(any())).thenReturn(4L);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        AgentActionExecutionService service = newService();

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "question.delete",
                true,
                Map.of("questionId", "3001"),
                null,
                QuestionDeletePlanTool.CONFIRMATION_TEXT
        ), "127.0.0.1");

        assertTrue(result.success());
        assertEquals("question.delete", result.actionCode());
        assertEquals(3001L, result.data().get("questionId"));
        assertEquals(2L, result.data().get("affectedPaperQuestionCount"));
        assertEquals(4L, result.data().get("affectedAnswerCount"));
        assertEquals("physical_delete", result.data().get("deleteMode"));

        verify(questionService).delete(3001L);
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertEquals(7, log.getUserId());
        assertEquals("agent.question.delete", log.getOperation());
        assertTrue(log.getParams().contains("\"actionCode\":\"question.delete\""));
        assertTrue(log.getParams().contains("\"questionId\":\"3001\""));
    }

    @Test
    void questionDeleteRejectsTeacherForOtherTeacherQuestion() {
        loginAs("teacher", 7);
        QuestionResponseDTO oldQuestion = questionResponse();
        oldQuestion.setTeacherId(8L);
        when(questionService.getById(3001L)).thenReturn(oldQuestion);
        when(courseMapper.selectById(5)).thenReturn(course(5, 8));
        AgentActionExecutionService service = newService();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest(
                        "question.delete",
                        true,
                        Map.of("questionId", 3001),
                        null,
                        QuestionDeletePlanTool.CONFIRMATION_TEXT
                ),
                ""
        ));

        assertEquals("只能删除当前登录教师负责的试题", exception.getMessage());
        verify(questionService, never()).delete(3001L);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    private AgentActionExecutionService newService() {
        NoticeCreateActionExecutor noticeExecutor = new NoticeCreateActionExecutor(
                noticeMapper,
                userMapper,
                teachingClassMapper
        );
        HomeworkCreateActionExecutor homeworkExecutor = new HomeworkCreateActionExecutor(
                homeworkMapper,
                teachingClassMapper,
                userMapper
        );
        HomeworkUpdateActionExecutor homeworkUpdateExecutor = new HomeworkUpdateActionExecutor(homeworkMapper);
        HomeworkDeleteActionExecutor homeworkDeleteExecutor = new HomeworkDeleteActionExecutor(homeworkMapper);
        HomeworkSubmissionGradeActionExecutor homeworkSubmissionGradeExecutor =
                new HomeworkSubmissionGradeActionExecutor(homeworkSubmitMapper, homeworkMapper);
        ExamSessionCreateActionExecutor examExecutor = new ExamSessionCreateActionExecutor(
                examService,
                teachingClassMapper,
                userMapper
        );
        ExamSessionCloseActionExecutor examCloseExecutor = new ExamSessionCloseActionExecutor(examService);
        GradeRecordCreateActionExecutor gradeRecordCreateExecutor = new GradeRecordCreateActionExecutor(
                gradeRecordMapper,
                userMapper,
                teachingClassMapper,
                courseMapper
        );
        GradeRecordDeleteActionExecutor gradeRecordDeleteExecutor = new GradeRecordDeleteActionExecutor(gradeRecordMapper);
        GradeRecordUpdateActionExecutor gradeRecordUpdateExecutor = new GradeRecordUpdateActionExecutor(gradeRecordMapper);
        ExamAnswerGradeActionExecutor examAnswerGradeExecutor = new ExamAnswerGradeActionExecutor(
                submissionService,
                examSessionMapper
        );
        ChapterDeleteActionExecutor chapterDeleteExecutor = new ChapterDeleteActionExecutor(
                chapterMapper,
                courseMapper,
                lessonMapper
        );
        QuestionCreateActionExecutor questionCreateExecutor = new QuestionCreateActionExecutor(
                questionService,
                courseMapper,
                userMapper
        );
        QuestionUpdateActionExecutor questionUpdateExecutor = new QuestionUpdateActionExecutor(
                questionService,
                courseMapper,
                userMapper
        );
        QuestionDeleteActionExecutor questionDeleteExecutor = new QuestionDeleteActionExecutor(
                questionService,
                courseMapper,
                examPaperQuestionMapper,
                examAnswerMapper
        );
        return new AgentActionExecutionService(List.of(noticeExecutor, homeworkExecutor, homeworkUpdateExecutor,
                homeworkDeleteExecutor, homeworkSubmissionGradeExecutor, examExecutor, examCloseExecutor,
                gradeRecordCreateExecutor, gradeRecordDeleteExecutor, gradeRecordUpdateExecutor, examAnswerGradeExecutor,
                chapterDeleteExecutor, questionCreateExecutor, questionUpdateExecutor, questionDeleteExecutor), operationLogMapper,
                new ObjectMapper(), pendingActionStore);
    }

    private ExamResponseDTO examResponse() {
        ExamResponseDTO response = new ExamResponseDTO();
        response.setId(99L);
        response.setClassId(3L);
        response.setTeacherId(7L);
        response.setPaperId(5L);
        response.setName("Java 期末考试");
        response.setStatus(ExamStatus.PUBLISHED);
        return response;
    }

    private ExamResponseDTO closedExamResponse() {
        ExamResponseDTO response = examResponse();
        response.setStatus(ExamStatus.CLOSED);
        return response;
    }

    private GradeRecord gradeRecord(Integer id, Integer teacherId) {
        GradeRecord grade = new GradeRecord();
        grade.setId(id);
        grade.setStudentId(30);
        grade.setClassId(3);
        grade.setCourseId(5);
        grade.setTeacherId(teacherId);
        grade.setGradeName("期末成绩");
        grade.setSourceType("manual");
        grade.setScore(new BigDecimal("80"));
        grade.setFullScore(new BigDecimal("100"));
        grade.setComment("原始成绩");
        return grade;
    }

    private Homework homework(Integer id, Integer teacherId) {
        Homework homework = new Homework();
        homework.setId(id);
        homework.setClassId(3);
        homework.setTeacherId(teacherId);
        homework.setTitle("第一章作业");
        homework.setContent("完成课后练习");
        homework.setAttachmentUrl("/uploads/homework/chapter1.docx");
        homework.setFullScore(new BigDecimal("100"));
        homework.setStartTime(LocalDateTime.parse("2026-07-05T09:00:00"));
        homework.setDeadline(LocalDateTime.parse("2026-07-06T18:00:00"));
        homework.setStatus(1);
        return homework;
    }

    private HomeworkSubmit homeworkSubmit(Integer id, Integer homeworkId) {
        HomeworkSubmit submit = new HomeworkSubmit();
        submit.setId(id);
        submit.setHomeworkId(homeworkId);
        submit.setStudentId(30);
        submit.setSubmitContent("已完成课后练习");
        submit.setAttachmentUrl("/uploads/homework-submit/chapter1-answer.docx");
        submit.setSubmitTime(LocalDateTime.parse("2026-07-06T12:00:00"));
        submit.setScore(new BigDecimal("80"));
        submit.setFeedback("原始反馈");
        submit.setGradeStatus(0);
        return submit;
    }

    private ExamRecord examRecord(Integer id, Integer examSessionId) {
        ExamRecord record = new ExamRecord();
        record.setId(id);
        record.setExamSessionId(examSessionId);
        record.setStudentId(30);
        record.setObjectiveScore(new BigDecimal("20"));
        record.setSubjectiveScore(BigDecimal.ZERO);
        record.setTotalScore(new BigDecimal("20"));
        return record;
    }

    private ExamSession examSession(Integer id, Integer teacherId) {
        ExamSession session = new ExamSession();
        session.setId(id);
        session.setTeacherId(teacherId);
        return session;
    }

    private Chapter chapter(Integer id, Integer courseId) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        chapter.setTitle("Java基础语法");
        chapter.setSortOrder(2);
        chapter.setStatus(Chapter.STATUS_NORMAL);
        return chapter;
    }

    private Course course(Integer id, Integer teacherId) {
        Course course = new Course();
        course.setId(id);
        course.setTeacherId(teacherId);
        course.setTitle("Java程序设计基础");
        return course;
    }

    private QuestionResponseDTO questionResponse() {
        QuestionResponseDTO response = new QuestionResponseDTO();
        response.setId(3001L);
        response.setCourseId(5L);
        response.setTeacherId(7L);
        response.setContent("Java 中 int 类型默认值是多少？");
        response.setType(QuestionType.SINGLE_CHOICE);
        response.setDifficulty(Difficulty.EASY);
        response.setKnowledgePoint("Java基础");
        response.setStatus(RecordStatus.ACTIVE);
        QuestionResponseDTO.OptionResponse optionA = new QuestionResponseDTO.OptionResponse();
        optionA.setOptionKey("A");
        optionA.setOptionContent("0");
        optionA.setSortOrder(1);
        QuestionResponseDTO.OptionResponse optionB = new QuestionResponseDTO.OptionResponse();
        optionB.setOptionKey("B");
        optionB.setOptionContent("null");
        optionB.setSortOrder(2);
        QuestionResponseDTO.AnswerResponse answer = new QuestionResponseDTO.AnswerResponse();
        answer.setAnswerContent("A");
        answer.setSortOrder(1);
        response.setOptions(List.of(optionA, optionB));
        response.setAnswers(List.of(answer));
        return response;
    }

    private QuestionResponseDTO updatedQuestionResponse() {
        QuestionResponseDTO response = questionResponse();
        response.setContent("Java 中 int 类型默认值是什么？");
        response.setAnalysis("int 是基本数据类型，默认值为 0。");
        return response;
    }

    private SubmissionResponseDTO submissionResponse() {
        SubmissionResponseDTO response = new SubmissionResponseDTO();
        response.setId(2001L);
        response.setExamId(99L);
        response.setStudentId(30L);
        response.setObjectiveScore(new BigDecimal("20"));
        response.setSubjectiveScore(new BigDecimal("8.5"));
        response.setTotalScore(new BigDecimal("28.5"));
        response.setStatus(SubmissionStatus.GRADED);
        return response;
    }

    private void loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        UserContext.set(user);
    }
}
