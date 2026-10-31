package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkStudyRequest {
    @NotBlank(message = "岗位名称不能为空")
    private String jobName;
    private String jobDesc;
    private String workLocation;
    private String workHours;
    @NotNull(message = "时薪标准不能为空")
    private BigDecimal hourlyRate;
    @NotNull(message = "最大招聘人数不能为空")
    private Integer maxRecruit;
    private Integer status;
}
