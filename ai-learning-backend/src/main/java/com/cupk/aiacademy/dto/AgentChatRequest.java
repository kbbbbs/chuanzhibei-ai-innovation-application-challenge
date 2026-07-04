package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentChatRequest {
    @NotBlank
    private String content;
}
