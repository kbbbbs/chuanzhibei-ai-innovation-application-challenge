package com.cupk.aiacademy.agent.core.llm;

import java.time.LocalDateTime;
import java.util.Map;

public class AgentModelHealthResponse {
    private boolean healthy;
    private String provider;
    private String model;
    private Long latencyMs;
    private String sample;
    private Map<String, Object> diagnostics;
    private LocalDateTime checkedAt;

    public static AgentModelHealthResponse healthy(String provider, String model, long latencyMs, String sample) {
        AgentModelHealthResponse response = base(provider, model, latencyMs);
        response.setHealthy(true);
        response.setSample(sample);
        return response;
    }

    public static AgentModelHealthResponse unhealthy(String provider,
                                                     String model,
                                                     long latencyMs,
                                                     AgentModelDiagnostics diagnostics) {
        AgentModelHealthResponse response = base(provider, model, latencyMs);
        response.setHealthy(false);
        response.setDiagnostics(diagnostics == null ? null : diagnostics.toData());
        return response;
    }

    private static AgentModelHealthResponse base(String provider, String model, long latencyMs) {
        AgentModelHealthResponse response = new AgentModelHealthResponse();
        response.setProvider(provider);
        response.setModel(model);
        response.setLatencyMs(latencyMs);
        response.setCheckedAt(LocalDateTime.now());
        return response;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getSample() {
        return sample;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }

    public Map<String, Object> getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
