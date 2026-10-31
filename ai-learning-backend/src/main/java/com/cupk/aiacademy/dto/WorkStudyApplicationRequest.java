package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkStudyApplicationRequest {
    @NotNull(message = "岗位编号不能为空")
    private Integer workStudyId;
    
    @NotNull(message = "学生编号不能为空")
    private Integer studentId;
}
