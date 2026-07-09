package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.teacherapply.TeacherApplication;
import com.cupk.aiacademy.teacherapply.TeacherApplicationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class ExamFileUploadControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthUtils authUtils;

    @MockitoBean
    private OperationLogMapper operationLogMapper;

    @Test
    void uploadExamAnswerFileWorks() throws Exception {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(9);
        user.setRoleCode("student");
        when(authUtils.requireLogin(eq("Bearer token"))).thenReturn(user);

        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "proof.pdf", "application/pdf", "answer".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/files/upload")
                        .file(file)
                        .param("bizType", "exam-answer")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.url", notNullValue()));
    }
}
