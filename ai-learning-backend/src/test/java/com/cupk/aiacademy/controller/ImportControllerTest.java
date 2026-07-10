package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.dto.imports.ImportConfirmResponse;
import com.cupk.aiacademy.dto.imports.ImportPreviewItem;
import com.cupk.aiacademy.dto.imports.ImportPreviewResponse;
import com.cupk.aiacademy.dto.imports.PaperImportConfirmRequest;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.service.ImportService;
import com.cupk.aiacademy.service.imports.TabularFileSupport.ExportFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@Import(GlobalExceptionHandler.class)
class ImportControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    @MockitoBean
    private OperationLogMapper operationLogMapper;

    @Test
    void templateDownloadsXlsxAttachment() throws Exception {
        byte[] file = new byte[]{'P', 'K', 1, 2};
        when(importService.template("questions")).thenReturn(file);

        mockMvc.perform(get("/api/imports/questions/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("questions-import-template.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(file));
    }

    @Test
    void examImportEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/api/imports/exams/template"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/imports/exams/preview"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/imports/exams/confirm")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportDownloadsRequestedFormat() throws Exception {
        byte[] file = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'};
        when(importService.exportData(eq("questions"), eq("csv"), anyMap()))
                .thenReturn(new ExportFile(file, "questions-export.csv", "text/csv;charset=UTF-8"));

        mockMvc.perform(get("/api/imports/questions/export?format=csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("questions-export.csv")))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().bytes(file));
    }

    @Test
    void paperPreviewRequiresCourseIdAndForwardsItToService() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "paper.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        mockMvc.perform(multipart("/api/imports/papers/preview").file(file))
                .andExpect(status().isBadRequest());

        when(importService.previewPapers(any(), eq(12L)))
                .thenReturn(new ImportPreviewResponse(java.util.List.of(ImportPreviewItem.valid(2, java.util.Map.of("content", "题目")))));
        mockMvc.perform(multipart("/api/imports/papers/preview").file(file).param("courseId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(1));
        verify(importService).previewPapers(any(), eq(12L));
    }

    @Test
    void paperConfirmForwardsSinglePaperMetadata() throws Exception {
        when(importService.confirmPapers(any()))
                .thenReturn(new ImportConfirmResponse(java.util.List.of()));

        mockMvc.perform(post("/api/imports/papers/confirm")
                        .contentType("application/json")
                        .content("""
                                {"name":"期末试卷","courseId":12,"durationMinutes":90,"status":"DRAFT","items":[{"rowNumber":2,"valid":true,"errors":[],"normalizedData":{}}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<PaperImportConfirmRequest> request = ArgumentCaptor.forClass(PaperImportConfirmRequest.class);
        verify(importService).confirmPapers(request.capture());
        org.junit.jupiter.api.Assertions.assertEquals("期末试卷", request.getValue().getName());
        org.junit.jupiter.api.Assertions.assertEquals(12L, request.getValue().getCourseId());
        org.junit.jupiter.api.Assertions.assertEquals(90, request.getValue().getDurationMinutes());
        org.junit.jupiter.api.Assertions.assertEquals(1, request.getValue().getItems().size());
    }

    @Test
    void legacyHomeworkImportConfirmationReturnsMigrationGuidance() throws Exception {
        mockMvc.perform(post("/api/imports/homeworks/confirm")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("旧作业写入接口已停用，请使用新的作业评测流程。"));
    }
}
