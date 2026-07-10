package com.cupk.aiacademy.dto.exports;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ExamScoreExportRow {
    @ExcelProperty("学生ID")
    private Long studentId;

    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("考试名称")
    private String examName;

    @ExcelProperty("成绩")
    private BigDecimal totalScore;

    @ExcelProperty("状态")
    private String status;
}
