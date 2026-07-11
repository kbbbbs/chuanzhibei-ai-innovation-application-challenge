package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.pojo.Course;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void previewAcceptsFileUploadQuestionWithoutCorrectAnswer() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        Course course = new Course();
        course.setTeacherId(2);
        when(permissionService.getCourse(1)).thenReturn(course);
        ImportService importService = new ImportService(
                mock(QuestionService.class),
                mock(PaperService.class),
                mock(ExamService.class),
                mock(HomeworkService.class),
                permissionService,
                new ObjectMapper()
        );

        byte[] template = importService.template("questions");
        byte[] upload;
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
             var output = new ByteArrayOutputStream()) {
            var row = workbook.getSheet("题目列表").getRow(1);
            row.getCell(0).setCellValue("1");
            row.getCell(1).setCellValue("文件上传题");
            row.getCell(2).setCellValue("请上传实验报告");
            for (int column = 3; column <= 8; column++) {
                row.getCell(column).setBlank();
            }
            row.getCell(9).setBlank();
            workbook.write(output);
            upload = output.toByteArray();
        }

        var preview = importService.preview("questions", new MockMultipartFile(
                "file", "questions.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", upload));

        assertThat(preview.getTotalRows()).isEqualTo(1);
        assertThat(preview.getInvalidRows()).isZero();
        assertThat(preview.getItems().getFirst().getErrors()).isEmpty();
    }
}
