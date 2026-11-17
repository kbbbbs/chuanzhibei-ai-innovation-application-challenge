package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.*;
import com.cupk.aiacademy.agent.tool.course.CourseCreatePlanTool;
import com.cupk.aiacademy.analysis.LearningAnalysisSnapshotService;
import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssistantPageActionPlannerTest {
    PlannerModelClient model;
    PermissionService permissions;
    AssistantPageActionPlanner planner;
    JdbcTemplate db;
    @BeforeEach void setup() {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE t_course_category(id INT PRIMARY KEY,name VARCHAR(100))");
        db.update("INSERT INTO t_course_category VALUES(5,'人工智能')");
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,title VARCHAR(100),teacher_id INT)");
        db.update("INSERT INTO t_course VALUES(266,'机器学习',7),(267,'重复课程',7),(268,'重复课程',7),(269,'他人课程',8)");
        model=mock(PlannerModelClient.class);permissions=mock(PermissionService.class);
        var actor=new AuthUtils.LoginUser();actor.setUserId(7);actor.setRoleCode("teacher");UserContext.set(actor);
        when(permissions.currentUser()).thenAnswer(c->UserContext.get());
        when(permissions.isAdmin()).thenReturn(false);
        when(permissions.isTeacher()).thenReturn(true);
        when(permissions.getCourse(266)).thenAnswer(c->{var course=new Course();course.setId(266);course.setTitle("机器学习");course.setTeacherId(7);return course;});
        doThrow(new ForbiddenException("无权维护该课程")).when(permissions).requireCourseTeacher(269);
        var json=new ObjectMapper();var properties=new AgentPlannerProperties();
        planner=new AssistantPageActionPlanner(model,json,
            new AgentActionCatalog(List.of(new CourseCreatePlanTool(null))),
            new LlmReadToolDecisionParser(json),new LlmReadToolDecisionValidator(properties,new AgentActionSchemaValidator()),
            permissions,new LearningAnalysisSnapshotService(db,permissions),db);
    }
    @AfterEach void clear(){ UserContext.clear(); }
    void decision(String args) { when(model.call(anyString())).thenReturn("{\"decision\":\"TOOL\",\"toolName\":\"page_fields\",\"confidence\":0.99,\"arguments\":"+args+"}"); }
    @Test void creationKeepsOnlyProvidedFieldsAndResolvesExistingCategory() {
        decision("{\"title\":\"人工智能基础\",\"categoryName\":\"人工智能\",\"difficulty\":\"beginner\"}");
        var result=planner.prepareCreation("新建人工智能基础，分类人工智能，难度入门");
        assertEquals("course-create",result.getPageAction().get("type"));
        var fields=(Map<?,?>)result.getPageAction().get("fields");
        assertEquals(5,fields.get("categoryId"));assertEquals("人工智能基础",fields.get("title"));
        assertFalse(fields.containsKey("description"));assertNull(result.getDirectReply().pendingAction());
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM t_course",Integer.class));
    }
    @Test void missingCreationFieldsStillOpensTheForm() {
        decision("{\"title\":\"新课程\"}");
        var fields=(Map<?,?>)planner.prepareCreation("新建一门叫新课程的课").getPageAction().get("fields");
        assertEquals("新课程",fields.get("title"));assertFalse(fields.containsKey("categoryId"));
    }
    @Test void unknownCategoryStaysUnselected() {
        decision("{\"title\":\"新课程\",\"categoryName\":\"不存在的分类\"}");
        var result=planner.prepareCreation("新建新课程，分类不存在的分类");
        assertFalse(((Map<?,?>)result.getPageAction().get("fields")).containsKey("categoryId"));
        assertTrue(result.getDirectReply().content().contains("分类"));
    }
    @Test void analysisResolvesOwnedCourseWithoutCreatingAnything() {
        decision("{\"courseTitle\":\"机器学习\"}");
        var result=planner.prepareAnalysis("分析一下机器学习学情");
        assertEquals("learning-analysis",result.getPageAction().get("type"));
        assertEquals(266,result.getPageAction().get("courseId"));
        verify(permissions).requireCourseTeacher(266);
    }
    @Test void unknownAndDuplicateCoursesClarifyWithoutNavigation() {
        decision("{\"courseTitle\":\"未知课程\"}");
        assertNull(planner.prepareAnalysis("分析未知课程学情").getPageAction());
        decision("{\"courseTitle\":\"重复课程\"}");
        assertNull(planner.prepareAnalysis("分析重复课程学情").getPageAction());
    }
    @Test void foreignCourseIsDenied() {
        decision("{\"courseId\":269}");
        assertNull(planner.prepareAnalysis("分析课程ID269的学情").getPageAction());
    }
    @Test void invalidModelFieldsAreRejected() {
        decision("{\"title\":\"新课程\",\"status\":1}");
        assertNull(planner.prepareCreation("新建新课程").getPageAction());
    }
    @Test void currentCourseScopeIsEnforced() {
        decision("{\"courseTitle\":\"机器学习\"}");
        var result=com.cupk.aiacademy.agent.tool.support.AgentToolScope.inCourse(267,
                ()->planner.prepareAnalysis("分析机器学习学情"));
        assertNull(result.getPageAction());
    }
    @Test void modelCannotInventACourseIdentifier() {
        decision("{\"courseId\":266}");
        assertNull(planner.prepareAnalysis("帮我分析一下学情").getPageAction());
    }
    @Test void adminAndStudentCannotPrepareCourseCreation() {
        when(permissions.isTeacher()).thenReturn(false);
        assertNull(planner.prepareCreation("新建课程").getPageAction());
        verifyNoInteractions(model);
    }
    @Test void courseIdMustAgreeWithItsProvidedTitle() {
        decision("{\"courseId\":266,\"courseTitle\":\"另一门课\"}");
        assertNull(planner.prepareAnalysis("分析课程266，另一门课学情").getPageAction());
    }
}
