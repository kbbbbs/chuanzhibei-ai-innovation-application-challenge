package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@TestPropertySource(properties = {
        "app.upload.dir=target/test-uploads",
        "app.upload.url-prefix=/uploads",
        "app.upload.max-file-size-bytes=1024"
})
class FileControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthUtils authUtils;

    @MockitoBean
    private OperationLogMapper operationLogMapper;

    @BeforeEach
    void setUpLogin() {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(3);
        user.setRoleCode("student");
        when(authUtils.requireLogin(eq("Bearer test-token"))).thenReturn(user);
    }

    @Test
    void uploadHomeworkAttachmentReturnsPublicUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "answer.md",
                "text/markdown",
                "homework answer".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                        .file(file)
                        .param("bizType", "homework")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.url", startsWith("/uploads/homework/")))
                .andExpect(jsonPath("$.data.originalName").value("answer.md"));
    }

    @Test
    void uploadRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "answer.exe",
                "application/octet-stream",
                "bad".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                        .file(file)
                        .param("bizType", "homework")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("文件类型不支持"));
    }
}
