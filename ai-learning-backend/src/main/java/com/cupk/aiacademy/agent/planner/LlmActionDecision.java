package com.cupk.aiacademy.agent.planner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmActionDecision {
    /**
     * ACTION: 生成系统操作计划
     * ASK_USER: 信息不足，需要用户补充
     * CHAT: 普通聊天，不走系统操作
     */
    private String decision;

    private String actionCode;

    private Map<String, Object> arguments;

    private List<String> missingRequiredFields;

    private String userMessage;

    private Double confidence;
}