package com.cupk.aiacademy.dto.imports;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class WorkStudyStudentExportRow {
    @ExcelProperty("岗位编号")
    private String workStudyId;

    @ExcelProperty("岗位名称")
    private String jobName;

    @ExcelProperty("学生编号")
    private String studentId;

    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("工作月份")
    private String workMonth;

    @ExcelProperty("工作时长")
    private String workHours;

    @ExcelProperty("适用时薪")
    private String hourlyRate;

    @ExcelProperty("合计金额")
    private String totalAmount;

    @ExcelProperty("状态")
    private String status;
}
