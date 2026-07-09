package com.cupk.aiacademy.service.imports;

import com.cupk.aiacademy.dto.imports.QuestionImportRow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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
                course_id,type,content,score,extra_column
                1,单选题,测试题目,5,ignored
                """.getBytes(StandardCharsets.UTF_8)
        );

        List<TabularFileSupport.TabularRow<QuestionImportRow>> rows = TabularFileSupport.read(file, QuestionImportRow.class);

        assertThat(rows).hasSize(1);
        QuestionImportRow row = rows.get(0).data();
        assertThat(row.getCourseId()).isEqualTo("1");
        assertThat(row.getType()).isEqualTo("单选题");
        assertThat(row.getContent()).isEqualTo("测试题目");
        assertThat(row.getScore()).isEqualTo("5");
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
}
