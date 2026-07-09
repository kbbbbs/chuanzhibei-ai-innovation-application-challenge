package com.cupk.aiacademy.agent.core.controller;

import com.cupk.aiacademy.agent.core.llm.AgentModelHealthService;
import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/model")
public class AgentModelController {
    private final AgentModelHealthService agentModelHealthService;

    public AgentModelController(AgentModelHealthService agentModelHealthService) {
        this.agentModelHealthService = agentModelHealthService;
    }

    @GetMapping("/health")
    public Result health() {
        return Result.success(agentModelHealthService.check());
    }
}
