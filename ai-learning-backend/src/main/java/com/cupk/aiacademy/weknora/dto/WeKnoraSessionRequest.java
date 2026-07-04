package com.cupk.aiacademy.weknora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeKnoraSessionRequest {
    private String knowledgeBaseId;
    private String title;
    private String agentId;
    private Map<String, Object> strategy;
}
