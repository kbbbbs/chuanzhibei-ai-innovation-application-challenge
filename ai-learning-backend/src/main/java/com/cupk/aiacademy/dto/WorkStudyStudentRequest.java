package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkStudyStudentRequest {
    @NotNull(message = "岗位编号不能为空")
    private Integer workStudyId;
    @NotNull(message = "学生编号不能为空")
    private Integer studentId;
    private String workMonth;
    private BigDecimal workHours;
    private BigDecimal hourlyRate;
    private Integer status;
}
