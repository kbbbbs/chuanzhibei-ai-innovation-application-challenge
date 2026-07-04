package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentSessionCreateRequest {
    @NotNull
    private Integer agentId;

    private Integer userId;

    @Size(max = 100)
    private String title;
}
