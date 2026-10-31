package com.cupk.aiacademy.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class WorkStudyStudentImportDTO {
    @ExcelProperty("学生编号")
    private String studentId;

    @ExcelProperty("工作月份")
    private String workMonth;

    @ExcelProperty("工作时长")
    private String workHours;

    @ExcelProperty("适用时薪")
    private String hourlyRate;
}
