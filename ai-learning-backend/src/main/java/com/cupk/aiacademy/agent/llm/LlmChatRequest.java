package com.cupk.aiacademy.agent.llm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LlmChatRequest {
    private String model;
    private Double temperature;
    private List<LlmMessage> messages = new ArrayList<>();
}
