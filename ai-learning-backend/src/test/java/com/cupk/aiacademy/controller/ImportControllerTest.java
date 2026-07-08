package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void templateReturnsApiResponseForBadType() throws Exception {
        when(importService.template("bad")).thenThrow(new BusinessException("不支持的导入类型: bad"));

        mockMvc.perform(get("/api/imports/bad/template"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("不支持的导入类型: bad"));
    }
}
