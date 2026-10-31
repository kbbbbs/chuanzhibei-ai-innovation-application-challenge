package com.cupk.aiacademy.dto.imports;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class HomeworkImportRow {
    @ExcelProperty("教学班ID")
    private String classId;

    @ExcelProperty("作业标题")
    private String title;

    @ExcelProperty("作业内容")
    private String content;

    @ExcelProperty("附件地址")
    private String attachmentUrl;

    @ExcelProperty("开始时间")
    private String startTime;

    @ExcelProperty("截止时间")
    private String deadline;

    @ExcelProperty("总分")
    private String fullScore;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("题目ID")
    private String questionId;

    @ExcelProperty("题号")
    private String questionNumber;

    @ExcelProperty("题型")
    private String type;

    @ExcelProperty("题面")
    private String questionContent;

    @ExcelProperty("题干")
    private String stem;

    @ExcelProperty("难度")
    private String difficulty;

    @ExcelProperty("知识点")
    private String knowledgePoint;

    @ExcelProperty("解析")
    private String analysis;

    @ExcelProperty("选项")
    private String options;

    @ExcelProperty("选项（单选题）")
    private String singleChoiceOptions;

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

    @ExcelProperty("答案")
    private String answers;

    @ExcelProperty("正确答案")
    private String correctAnswers;

    @ExcelProperty("正确答案（单选题）")
    private String singleChoiceAnswer;

    @ExcelProperty("题目分值")
    private String score;

    @ExcelProperty("排序")
    private String sortOrder;
}
