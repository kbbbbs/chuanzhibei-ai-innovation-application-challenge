package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.JwtUtils;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.GradeImportLogMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import com.cupk.aiacademy.service.CourseResourceService;
import com.cupk.aiacademy.service.CourseImportService;
import com.cupk.aiacademy.service.HomeworkService;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        AuthController.class,
        CategoryController.class,
        OperationLogController.class,
        ClassStudentController.class,
        LearningProgressController.class,
        HomeworkController.class,
        HomeworkSubmitController.class,
        GradeImportLogController.class,
        RoleController.class,
        UserController.class,
        GradeRecordController.class,
        CourseController.class
})
@Import(GlobalExceptionHandler.class)
class ApiRepairRegressionTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private RoleMapper roleMapper;

    @MockitoBean
    private UserRoleMapper userRoleMapper;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private AuthUtils authUtils;

    @MockitoBean
    private CourseCategoryMapper categoryMapper;

    @MockitoBean
    private CourseMapper courseMapper;

    @MockitoBean
    private OperationLogMapper operationLogMapper;

    @MockitoBean
    private ClassStudentMapper classStudentMapper;

    @MockitoBean
    private TeachingClassMapper teachingClassMapper;

    @MockitoBean
    private LearningProgressMapper learningProgressMapper;

    @MockitoBean
    private LessonMapper lessonMapper;

    @MockitoBean
    private ChapterMapper chapterMapper;

    @MockitoBean
    private HomeworkSubmitMapper homeworkSubmitMapper;

    @MockitoBean
    private HomeworkMapper homeworkMapper;

    @MockitoBean
    private HomeworkService homeworkService;

    @MockitoBean
    private ExamPaperQuestionMapper paperQuestionMapper;

    @MockitoBean
    private ExamPaperMapper paperMapper;

    @MockitoBean
    private QuestionMapper questionMapper;

    @MockitoBean
    private ExamRecordMapper examRecordMapper;

    @MockitoBean
    private ExamSessionMapper sessionMapper;

    @MockitoBean
    private ExamAnswerMapper answerMapper;

    @MockitoBean
    private GradeImportLogMapper importLogMapper;

    @MockitoBean
    private GradeRecordMapper gradeRecordMapper;

    @MockitoBean
    private CourseResourceService courseResourceService;

    @MockitoBean
    private CourseImportService courseImportService;

    @MockitoBean
    private PermissionService permissionService;

    @BeforeEach
    void setUpPermission() {
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser();
        admin.setUserId(1);
        admin.setUsername("admin");
        admin.setRoleCode("admin");
        when(permissionService.currentUser()).thenReturn(admin);
        when(authUtils.requireLogin(any())).thenReturn(admin);
        when(authUtils.isAdmin(any())).thenAnswer(invocation -> {
            AuthUtils.LoginUser user = invocation.getArgument(0);
            return user != null && "admin".equals(user.getRoleCode());
        });
        when(authUtils.isTeacher(any())).thenAnswer(invocation -> {
            AuthUtils.LoginUser user = invocation.getArgument(0);
            return user != null && "teacher".equals(user.getRoleCode());
        });
        when(authUtils.isStudent(any())).thenAnswer(invocation -> {
            AuthUtils.LoginUser user = invocation.getArgument(0);
            return user != null && "student".equals(user.getRoleCode());
        });
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer test-token");
    }

    @Test
    void loginNullBodyReturnsUnifiedResult() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("500"));
    }

    @Test
    void loginSuccessReturnsToken() throws Exception {
        User user = new User();
        user.setId(1);
        user.setUsername("admin");
        user.setPassword("e10adc3949ba59abbe56e057f20f883e");
        user.setStatus(1);

        Role role = new Role();
        role.setId(1);
        role.setRoleCode("admin");

        UserRole userRole = new UserRole();
        userRole.setUserId(1);
        userRole.setRoleId(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectOne(any())).thenReturn(userRole);
        when(roleMapper.selectById(1)).thenReturn(role);
        when(jwtUtils.createToken(1, "admin", "admin")).thenReturn("token-value");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.token").value("token-value"));
    }

    @Test
    void nullBodyCreateApisReturnUnifiedBadRequestResult() throws Exception {
        mockMvc.perform(authed(post("/categories").contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));

        mockMvc.perform(authed(post("/operation-logs").contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void emptyObjectApisReturnBadRequestBeforeLookupExceptRetiredHomeworkWrites() throws Exception {
        mockMvc.perform(authed(post("/class-students").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));

        mockMvc.perform(authed(post("/learning-progress").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));

        mockMvc.perform(authed(post("/homework-submits").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"));

        mockMvc.perform(authed(post("/grade-import-logs").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));

        mockMvc.perform(authed(put("/learning-progress/9001").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void legacyHomeworkSubmitRejectsPayloadValidationRequests() throws Exception {
        mockMvc.perform(authed(post("/homework-submits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeworkId\":1,\"submitContent\":\"  \",\"attachmentUrl\":\"\"}")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"))
                .andExpect(jsonPath("$.msg").value("旧作业写入接口已停用，请使用新的作业评测流程。"));
    }

    @Test
    void legacyHomeworkWritesReturnMigrationGuidanceWhileReadsRemainAvailable() throws Exception {
        mockMvc.perform(authed(get("/homeworks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        mockMvc.perform(authed(get("/homework-submits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        mockMvc.perform(authed(post("/homeworks").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"))
                .andExpect(jsonPath("$.msg").value("旧作业写入接口已停用，请使用新的作业评测流程。"));
        mockMvc.perform(authed(put("/homeworks/1").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"));
        mockMvc.perform(authed(delete("/homeworks/1")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"));
        mockMvc.perform(authed(post("/homework-submits").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"));
        mockMvc.perform(authed(put("/homework-submits/1").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"));
        mockMvc.perform(authed(delete("/homework-submits/1")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"));
    }

    @Test
    void duplicateRoleCodeReturnsConflictResult() throws Exception {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        mockMvc.perform(authed(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"dup\",\"roleCode\":\"student\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("409"));
    }

    @Test
    void duplicateClassStudentReturnsConflictResult() throws Exception {
        when(teachingClassMapper.selectById(1)).thenReturn(new TeachingClass());
        when(userMapper.selectById(3)).thenReturn(new User());
        when(classStudentMapper.selectCount(any())).thenReturn(1L);

        mockMvc.perform(authed(post("/class-students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":1,\"studentId\":3,\"status\":1}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("409"));
    }

    @Test
    void deleteReferencedRoleAndUserReturnConflictResult() throws Exception {
        when(roleMapper.selectById(1)).thenReturn(new Role());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        mockMvc.perform(authed(delete("/roles/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("409"));

        when(userMapper.selectById(2)).thenReturn(new User());
        when(courseMapper.selectCount(any())).thenReturn(1L);

        mockMvc.perform(authed(delete("/users/2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("409"));
    }

    @Test
    void invalidGradeSourceReturnBadRequestResult() throws Exception {
        when(userMapper.selectById(3)).thenReturn(new User());
        when(userMapper.selectById(2)).thenReturn(new User());
        when(courseMapper.selectById(1)).thenReturn(new Course());
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(1);
        teachingClass.setCourseId(1);
        teachingClass.setTeacherId(2);
        when(teachingClassMapper.selectById(1)).thenReturn(teachingClass);
        when(permissionService.isStudentInClass(1, 3)).thenReturn(true);

        mockMvc.perform(authed(post("/grade-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":3,\"classId\":1,\"courseId\":1,\"teacherId\":2,\"gradeName\":\"期末\",\"sourceType\":\"bad_source\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void courseEnableMakesOwnedCourseAvailableToStudents() throws Exception {
        Course course = new Course();
        course.setId(1);
        course.setStatus(0);
        when(courseMapper.selectById(1)).thenReturn(course);
        when(courseMapper.updateById(course)).thenReturn(1);

        mockMvc.perform(authed(put("/courses/1/enable")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        org.junit.jupiter.api.Assertions.assertEquals(1, course.getStatus());
    }

    @Test
    void invalidQueryParametersReturnHttp400WithResult() throws Exception {
        mockMvc.perform(authed(get("/courses").param("pageNum", "abc").param("pageSize", "5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        mockMvc.perform(authed(get("/users").param("status", "enabled")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }
}
