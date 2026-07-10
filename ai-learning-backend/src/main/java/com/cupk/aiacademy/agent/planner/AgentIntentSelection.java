package com.cupk.aiacademy.agent.planner;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentIntentSelection {
    public enum Type {
        CHAT,
        READ,
        WRITE
    }

    private Type type;
    private String code;

    public static AgentIntentSelection chat() {
        return new AgentIntentSelection(Type.CHAT, "");
    }

    public static AgentIntentSelection read(String toolName) {
        return new AgentIntentSelection(Type.READ, toolName);
    }

    public static AgentIntentSelection write(String actionCode) {
        return new AgentIntentSelection(Type.WRITE, actionCode);
    }
}
