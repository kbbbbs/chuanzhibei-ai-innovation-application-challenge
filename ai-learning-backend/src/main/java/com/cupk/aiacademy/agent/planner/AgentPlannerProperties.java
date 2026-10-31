package com.cupk.aiacademy.agent.planner;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.planner")
@Data
public class AgentPlannerProperties {
    private boolean enabled = false;
    private String mode = "shadow";
    private int maxRepairRounds = 1;
    private double minConfidence = 0.6;
    private String model = "";
    private int timeoutSeconds = 5;
    private int parameterTimeoutSeconds = 10;
    private int maxOutputTokens = 600;
    private double temperature = 0.0;
}
