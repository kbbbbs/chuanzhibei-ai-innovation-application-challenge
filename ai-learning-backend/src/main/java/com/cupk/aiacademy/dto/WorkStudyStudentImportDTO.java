package com.cupk.aiacademy.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkStudyStudentImportDTO {
    @ExcelProperty("学生编号")
    private Integer studentId;

    @ExcelProperty("工作月份")
    private String workMonth;

    @ExcelProperty("工作时长")
    private BigDecimal workHours;

    @ExcelProperty("适用时薪")
    private BigDecimal hourlyRate;
}
