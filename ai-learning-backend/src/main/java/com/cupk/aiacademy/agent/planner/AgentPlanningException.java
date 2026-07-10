package com.cupk.aiacademy.agent.planner;

public class AgentPlanningException extends RuntimeException {
    public AgentPlanningException(String message) {
        super(message);
    }

    public AgentPlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
