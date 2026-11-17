package com.cupk.aiacademy.agent.tool.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.*;
import com.cupk.aiacademy.pojo.*;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedAgentToolScopeTest {
    AgentToolSupport support;
    PermissionService permissions;
    @BeforeEach void setup() {
        support = new AgentToolSupport(mock(HomeworkSubmitMapper.class), mock(LearningProgressMapper.class),
                mock(ChapterMapper.class), mock(LessonMapper.class), mock(CourseMapper.class),
                mock(TeachingClassMapper.class), mock(ClassStudentMapper.class));
        permissions = mock(PermissionService.class);
        ReflectionTestUtils.setField(support,"permissionService",permissions);
    }
    @AfterEach void clear() { UserContext.clear(); }
    void login(String role) {
        var actor = new AuthUtils.LoginUser();
        actor.setUserId(4);
        actor.setRoleCode(role);
        UserContext.set(actor);
    }
    @Test void studentUsesPublishedAndOpenedEligibilityService() {
        login("student");
        when(permissions.studentCourseIds(4)).thenReturn(List.of(129));
        when(permissions.studentLearningClassIds(4)).thenReturn(List.of(1));
        assertEquals(List.of(129),support.findVisibleCourseIds());
        assertEquals(List.of(1),support.findVisibleClassIds());
        verify(permissions).studentCourseIds(4);
        verify(permissions).studentLearningClassIds(4);
    }
    @Test void fixedCourseCannotExpandToAnotherEligibleCourse() {
        login("student");
        when(permissions.studentCourseIds(4)).thenReturn(List.of(129,130));
        assertEquals(List.of(129), AgentToolScope.inCourse(129, support::findVisibleCourseIds));
        assertEquals(List.of(129,130), support.findVisibleCourseIds());
    }
    @Test void adminCannotQueryLessonBodies() {
        login("admin");
        assertFalse(support.applyChapterVisibility(new QueryWrapper<Chapter>()));
        assertFalse(support.applyLessonVisibility(new QueryWrapper<Lesson>()));
    }
    @Test void fixedCourseRejectsReadToolsWithoutCourseFiltering() {
        var tool = mock(com.cupk.aiacademy.agent.tool.api.AgentTool.class);
        when(tool.name()).thenReturn("exam_record_search");
        when(tool.declaredOperationType()).thenReturn(com.cupk.aiacademy.agent.tool.api.AgentToolOperationType.READ);
        when(tool.aliases()).thenReturn(java.util.Set.of());
        var executor = new com.cupk.aiacademy.agent.tool.AgentToolExecutor(List.of(tool));
        AgentToolScope.inCourse(129, () -> executor.executeReadTool("exam_record_search", Map.of("query","成绩")));
        verify(tool,never()).execute(any());
    }
    @Test void scopeIsClearedWhenToolFails() {
        assertThrows(IllegalStateException.class, () -> AgentToolScope.inCourse(129, () -> { throw new IllegalStateException("fail"); }));
        assertNull(AgentToolScope.courseId());
    }
}
