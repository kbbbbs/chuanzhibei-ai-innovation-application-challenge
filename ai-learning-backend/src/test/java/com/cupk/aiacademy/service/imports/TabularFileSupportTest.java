package com.cupk.aiacademy.service.imports;

import com.cupk.aiacademy.dto.imports.QuestionImportRow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TabularFileSupportTest {
    @Test
    void readsCsvWithExtraColumnsAndFieldNameHeaders() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.csv",
                "text/csv",
                """
                course_id,type,content,answers,extra_column
                1,单选题,测试题目,A,ignored
                """.getBytes(StandardCharsets.UTF_8)
        );

        List<TabularFileSupport.TabularRow<QuestionImportRow>> rows = TabularFileSupport.read(file, QuestionImportRow.class);

        assertThat(rows).hasSize(1);
        QuestionImportRow row = rows.get(0).data();
        assertThat(row.getCourseId()).isEqualTo("1");
        assertThat(row.getType()).isEqualTo("单选题");
        assertThat(row.getContent()).isEqualTo("测试题目");
        assertThat(row.getAnswers()).isEqualTo("A");
        assertThat(row.getDifficulty()).isNull();
    }

    @Test
    void writesCsvWithTemplateHeaders() {
        QuestionImportRow row = new QuestionImportRow();
        row.setCourseId("1");
        row.setType("简答题");
        row.setContent("说明 JVM");
        row.setAnswers("参考答案");

        byte[] file = TabularFileSupport.write("csv", QuestionImportRow.class, List.of(row));
        String csv = new String(file, StandardCharsets.UTF_8);

        assertThat(csv).contains("课程ID,题型,题干");
        assertThat(csv).contains("1,简答题,说明 JVM");
    }

    @Test
    void readsNamedQuestionSheetInsteadOfGuideSheet() throws Exception {
        byte[] workbookBytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("填写说明").createRow(0).createCell(0).setCellValue("请填写题目列表");
            var questionSheet = workbook.createSheet("题目列表");
            var header = questionSheet.createRow(0);
            header.createCell(0).setCellValue("课程ID");
            header.createCell(1).setCellValue("题型");
            header.createCell(2).setCellValue("题干");
            header.createCell(3).setCellValue("正确答案");
            var row = questionSheet.createRow(1);
            row.createCell(0).setCellValue("1");
            row.createCell(1).setCellValue("简答题");
            row.createCell(2).setCellValue("说明 JVM");
            row.createCell(3).setCellValue("JVM 是 Java 虚拟机");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        List<TabularFileSupport.TabularRow<QuestionImportRow>> rows = TabularFileSupport.read(file, QuestionImportRow.class, "题目列表");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().data().getContent()).isEqualTo("说明 JVM");
    }
}
