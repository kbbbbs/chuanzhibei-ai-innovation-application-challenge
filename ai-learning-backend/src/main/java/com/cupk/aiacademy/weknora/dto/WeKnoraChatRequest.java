package com.cupk.aiacademy.weknora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
public class WeKnoraChatRequest {
    private String query;
    private String knowledgeBaseId;
    private String agentId;
    private Map<String, Object> params;
}
