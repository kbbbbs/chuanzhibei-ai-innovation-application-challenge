package com.cupk.aiacademy.dto;

import lombok.Data;

import java.util.List;

@Data
public class GenerateQuestionsFromKbRequest {
    private String knowledgeBaseId;
    private Long courseId;
    private Integer agentId;
    private Integer count;
    private List<String> types;
    private String difficulty;
    private String instruction;
}
