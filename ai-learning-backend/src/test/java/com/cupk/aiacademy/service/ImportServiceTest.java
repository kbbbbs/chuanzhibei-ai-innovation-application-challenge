package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {
    @Test
    void templateGeneratesXlsxFileForQuestions() {
        PermissionService permissionService = mock(PermissionService.class);
        ImportService importService = new ImportService(
                mock(QuestionService.class),
                mock(PaperService.class),
                mock(ExamService.class),
                mock(HomeworkService.class),
                permissionService,
                new ObjectMapper()
        );

        byte[] file = importService.template("questions");

        verify(permissionService).requireTeacherOrAdmin();
        assertThat(file).isNotEmpty();
        assertThat(file[0]).isEqualTo((byte) 'P');
        assertThat(file[1]).isEqualTo((byte) 'K');
    }

    @Test
    void templateRejectsUnknownTypeWithReadableMessage() {
        ImportService importService = new ImportService(
                mock(QuestionService.class),
                mock(PaperService.class),
                mock(ExamService.class),
                mock(HomeworkService.class),
                mock(PermissionService.class),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> importService.template("bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的导入类型");
    }

    @Test
    void questionTemplateContainsGuideAndQuestionSheets() throws Exception {
        ImportService importService = new ImportService(
                mock(QuestionService.class),
                mock(PaperService.class),
                mock(ExamService.class),
                mock(HomeworkService.class),
                mock(PermissionService.class),
                new ObjectMapper()
        );

        byte[] file = importService.template("questions");

        try (var workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(file))) {
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.getSheet("题目列表")).isNotNull();
            assertThat(workbook.getSheet("题目列表").getRow(0).getCell(0).getStringCellValue()).isEqualTo("课程ID");
            assertThat(workbook.getSheet("题目列表").getRow(0).getCell(9).getStringCellValue()).isEqualTo("正确答案");
        }
    }
}
