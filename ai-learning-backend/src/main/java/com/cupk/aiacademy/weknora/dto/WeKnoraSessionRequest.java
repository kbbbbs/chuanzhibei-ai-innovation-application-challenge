package com.cupk.aiacademy.weknora.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeKnoraSessionRequest {
    @JsonProperty("knowledgeBaseId")
    private String knowledgeBaseId;
    private String title;
    @JsonProperty("agentId")
    private String agentId;
    private Map<String, Object> strategy;
}
