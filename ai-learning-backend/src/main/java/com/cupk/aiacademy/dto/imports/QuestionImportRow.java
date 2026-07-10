package com.cupk.aiacademy.dto.imports;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class QuestionImportRow {
    @ExcelProperty("课程ID")
    private String courseId;

    @ExcelProperty("题型")
    private String type;

    @ExcelProperty("题干")
    private String content;

    @ExcelProperty("难度")
    private String difficulty;

    @ExcelProperty("知识点")
    private String knowledgePoint;

    @ExcelProperty("解析")
    private String analysis;

    @ExcelProperty("选项A")
    private String optionA;

    @ExcelProperty("选项B")
    private String optionB;

    @ExcelProperty("选项C")
    private String optionC;

    @ExcelProperty("选项D")
    private String optionD;

    @ExcelProperty("选项E")
    private String optionE;

    @ExcelProperty("选项F")
    private String optionF;

    @ExcelProperty("正确答案")
    private String answers;
}
