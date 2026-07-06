package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkStudyAuditRequest {
    @NotNull(message = "申请ID不能为空")
    private Integer applicationId;
    
    @NotNull(message = "审核结果不能为空")
    private Integer auditStatus; // 1通过，2拒绝
    
    private String rejectReason; // 拒绝原因
    
    @NotNull(message = "审核人不能为空")
    private Integer auditorId; // 审核人ID
}
