package com.cupk.aiacademy.agent.action.notice;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.notice.NoticeStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.agent.query.AgentWriteEntityLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeActionExecutorTest {
    @Mock
    private NoticeMapper noticeMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private UserMapper userMapper;

    private AgentWriteEntityLocator entityLocator;

    @org.junit.jupiter.api.BeforeEach
    void setUpLocator() {
        entityLocator = new AgentWriteEntityLocator(null, noticeMapper, null);
    }

    @Test
    void noticeCreateInsertsNoticeForTeacher() {
        when(userMapper.selectById(7)).thenReturn(new com.cupk.aiacademy.pojo.User());
        when(noticeMapper.insert(any(Notice.class))).thenAnswer(invocation -> {
            Notice notice = invocation.getArgument(0);
            notice.setId(201);
            return 1;
        });
        NoticeCreateActionExecutor executor =
                new NoticeCreateActionExecutor(noticeMapper, userMapper, teachingClassMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("title", "考试安排", "content", "明天上午十点考试"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("notice.create", result.actionCode());
        assertEquals(201, result.data().get("noticeId"));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(captor.capture());
        Notice notice = captor.getValue();
        assertEquals("考试安排", notice.getTitle());
        assertEquals("明天上午十点考试", notice.getContent());
        assertEquals(7, notice.getPublisherId());
        assertEquals(1, notice.getStatus());
    }

    @Test
    void noticeCreateRejectsStudentRole() {
        NoticeCreateActionExecutor executor =
                new NoticeCreateActionExecutor(noticeMapper, userMapper, teachingClassMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("student", 9),
                        Map.of("title", "考试安排", "content", "明天上午十点考试"),
                        ""
                )
        ));

        assertEquals("只有教师或管理员可以发布通知", exception.getMessage());
        verify(noticeMapper, never()).insert(any(Notice.class));
    }

    @Test
    void noticeUpdateUpdatesAllowedFieldsForOwnerTeacher() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 7));
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "noticeId", "201",
                        "title", "期末考试安排",
                        "content", "明天上午十点期末考试",
                        "noticeType", "exam"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("notice.update", result.actionCode());
        assertEquals("考试安排", result.data().get("oldTitle"));
        assertEquals("期末考试安排", result.data().get("newTitle"));
        assertEquals("明天上午十点考试", result.data().get("oldContent"));
        assertEquals("明天上午十点期末考试", result.data().get("newContent"));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).updateById(captor.capture());
        Notice notice = captor.getValue();
        assertEquals(201, notice.getId());
        assertEquals("期末考试安排", notice.getTitle());
        assertEquals("明天上午十点期末考试", notice.getContent());
        assertEquals("exam", notice.getNoticeType());
        assertEquals(7, notice.getPublisherId());
        assertEquals(1, notice.getStatus());
    }

    @Test
    void noticeUpdateResolvesNoticeByOriginalTitle() {
        Notice matched = notice(201, 7);
        matched.setTitle("考试安排");
        when(noticeMapper.selectList(any())).thenReturn(List.of(matched));
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("originalTitle", "考试安排", "title", "期末考试安排"),
                ""
        ));

        assertTrue(result.success());
        assertEquals(201, result.data().get("noticeId"));
        assertEquals("考试安排", result.data().get("oldTitle"));
        assertEquals("期末考试安排", result.data().get("newTitle"));
    }

    @Test
    void noticeUpdateValidatesTargetClassWhenProvided() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 7));
        when(teachingClassMapper.selectById(5)).thenReturn(new TeachingClass());
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("noticeId", "201", "classId", "5"),
                ""
        ));

        assertTrue(result.success());
        assertEquals(5, result.data().get("newClassId"));
    }

    @Test
    void noticeUpdateRejectsNonexistentTargetClass() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 7));
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("noticeId", 201, "classId", 99),
                        ""
                )
        ));

        assertEquals("教学班不存在", exception.getMessage());
        verify(noticeMapper, never()).updateById(any(Notice.class));
    }

    @Test
    void noticeUpdateRejectsOtherPublisherNotice() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 8));
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("noticeId", 201, "title", "期末考试安排"),
                        ""
                )
        ));

        assertEquals("只能修改当前登录用户发布的通知", exception.getMessage());
        verify(noticeMapper, never()).updateById(any(Notice.class));
    }

    @Test
    void noticeUpdateRejectsMissingUpdateField() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 7));
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("noticeId", 201),
                        ""
                )
        ));

        assertEquals("至少需要提供一个可修改的通知字段", exception.getMessage());
        verify(noticeMapper, never()).updateById(any(Notice.class));
    }

    @Test
    void noticeUpdateRejectsAmbiguousTitle() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice(201, 7), notice(202, 7)));
        NoticeUpdateActionExecutor executor =
                new NoticeUpdateActionExecutor(noticeMapper, teachingClassMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("originalTitle", "考试安排", "title", "期末考试安排"),
                        ""
                )
        ));

        assertTrue(exception.getMessage().contains("匹配到多条通知"));
        assertTrue(exception.getMessage().contains("noticeId"));
        verify(noticeMapper, never()).updateById(any(Notice.class));
    }

    @Test
    void noticeStatusUpdatesStatusForOwnerTeacher() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 7));
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        NoticeStatusActionExecutor executor = new NoticeStatusActionExecutor(noticeMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("noticeId", "201", "status", 0, "reason", "通知撤回"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("notice.status", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(NoticeStatusPlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(1, result.data().get("oldStatus"));
        assertEquals(0, result.data().get("newStatus"));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).updateById(captor.capture());
        Notice notice = captor.getValue();
        assertEquals(201, notice.getId());
        assertEquals(0, notice.getStatus());
    }

    @Test
    void noticeStatusRejectsInvalidStatus() {
        NoticeStatusActionExecutor executor = new NoticeStatusActionExecutor(noticeMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("noticeId", 201, "status", 9),
                        ""
                )
        ));

        assertEquals("通知状态只能是 0 或 1", exception.getMessage());
        verify(noticeMapper, never()).updateById(any(Notice.class));
    }

    @Test
    void noticeStatusResolvesNoticeByTitle() {
        Notice matched = notice(201, 7);
        matched.setTitle("考试安排");
        when(noticeMapper.selectList(any())).thenReturn(List.of(matched));
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        NoticeStatusActionExecutor executor = new NoticeStatusActionExecutor(noticeMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of("title", "考试安排", "status", 0),
                ""
        ));

        assertTrue(result.success());
        assertEquals(201, result.data().get("noticeId"));
        assertEquals(0, result.data().get("newStatus"));
    }

    @Test
    void noticeStatusRejectsOtherPublisherNotice() {
        when(noticeMapper.selectById(201)).thenReturn(notice(201, 8));
        NoticeStatusActionExecutor executor = new NoticeStatusActionExecutor(noticeMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("noticeId", 201, "status", 0),
                        ""
                )
        ));

        assertEquals("只能变更当前登录用户发布通知的状态", exception.getMessage());
        verify(noticeMapper, never()).updateById(any(Notice.class));
    }

    private AuthUtils.LoginUser loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        return user;
    }

    private Notice notice(Integer id, Integer publisherId) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setPublisherId(publisherId);
        notice.setTitle("考试安排");
        notice.setContent("明天上午十点考试");
        notice.setNoticeType("general");
        notice.setStatus(1);
        return notice;
    }
}
