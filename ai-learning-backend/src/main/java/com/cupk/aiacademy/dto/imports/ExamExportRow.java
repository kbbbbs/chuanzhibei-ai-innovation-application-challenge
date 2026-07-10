package com.cupk.aiacademy.dto.imports;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ExamExportRow {
    @ExcelProperty("考试名称")
    private String name;

    @ExcelProperty("教学班ID")
    private String classId;

    @ExcelProperty("试卷ID")
    private String paperId;

    @ExcelProperty("试卷名称")
    private String paperName;

    @ExcelProperty("开始时间")
    private String startTime;

    @ExcelProperty("结束时间")
    private String endTime;

    @ExcelProperty("时长")
    private String durationMinutes;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("说明")
    private String description;
}
