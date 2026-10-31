package com.cupk.aiacademy.dto.imports;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class PaperExportRow {
    @ExcelProperty("试卷名称")
    private String name;

    @ExcelProperty("课程ID")
    private String courseId;

    @ExcelProperty("时长")
    private String durationMinutes;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("题目ID")
    private String questionId;

    @ExcelProperty("题号")
    private String questionNumber;

    @ExcelProperty("题干")
    private String content;

    @ExcelProperty("题目分值")
    private String score;

    @ExcelProperty("排序")
    private String sortOrder;
}
