package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentCreateRequest {
    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    private String systemPrompt;

    @Size(max = 100)
    private String model;

    @DecimalMin("0.00")
    @DecimalMax("2.00")
    private BigDecimal temperature;

    private String knowledgeBaseIds;
    private String enabledTools;
    private Integer status;
}
