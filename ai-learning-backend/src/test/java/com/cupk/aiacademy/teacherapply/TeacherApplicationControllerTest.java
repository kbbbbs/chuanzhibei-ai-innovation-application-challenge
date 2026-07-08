package com.cupk.aiacademy.teacherapply;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.config.AuthInterceptor;
import com.cupk.aiacademy.config.WebMvcConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherApplicationController.class)
@Import({AuthInterceptor.class, WebMvcConfiguration.class})
class TeacherApplicationControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private TeacherApplicationService service;
    @MockitoBean
    private AuthUtils authUtils;

    @Test
    void publicTeacherApplicationDoesNotNeedLogin() throws Exception {
        TeacherApplication app = new TeacherApplication();
        app.setId(1);
        app.setUsername("teacher_apply");
        app.setStatus(0);
        when(service.apply(any())).thenReturn(app);

        mockMvc.perform(post("/teacher-applications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(newRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.username").value("teacher_apply"));
    }

    @Test
    void listingTeacherApplicationsRequiresLogin() throws Exception {
        mockMvc.perform(get("/teacher-applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListTeacherApplicationsWithToken() throws Exception {
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser();
        admin.setUserId(1);
        admin.setUsername("admin");
        admin.setRoleCode("admin");
        when(authUtils.requireLogin("Bearer admin-token")).thenReturn(admin);

        TeacherApplication app = new TeacherApplication();
        app.setId(1);
        app.setUsername("teacher_apply");
        app.setStatus(0);
        Page<TeacherApplication> page = new Page<>(1, 100);
        page.setRecords(List.of(app));
        page.setTotal(1);
        when(service.list(eq(0), eq(1), eq(100))).thenReturn(page);

        mockMvc.perform(get("/teacher-applications")
                        .header("Authorization", "Bearer admin-token")
                        .param("status", "0")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.records[0].username").value("teacher_apply"));
    }

    private TeacherApplicationRequest newRequest() {
        TeacherApplicationRequest request = new TeacherApplicationRequest();
        request.setUsername("teacher_apply");
        request.setPassword("secret123");
        request.setNickname("申请教师");
        request.setReason("希望创建互动课程");
        return request;
    }
}
