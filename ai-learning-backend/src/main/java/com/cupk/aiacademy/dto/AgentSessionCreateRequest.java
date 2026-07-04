package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentSessionCreateRequest {
    @Positive(message = "agentId 必须为正数")
    private Integer agentId;

    @Positive(message = "userId 必须为正数")
    private Integer userId;

    @Size(max = 100, message = "会话标题不能超过100字")
    private String title;
}
