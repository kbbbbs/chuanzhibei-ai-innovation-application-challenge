package com.cupk.aiacademy.agent.controller;

import com.cupk.aiacademy.agent.service.AgentDefinitionService;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 定义管理接口。
 * 用于维护可选 Agent 的提示词、模型参数、知识库和工具配置。
 */
@RestController
@RequestMapping("/api/agent/definitions")
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;

    public AgentDefinitionController(AgentDefinitionService agentDefinitionService) {
        this.agentDefinitionService = agentDefinitionService;
    }

    @GetMapping
    public Result listDefinitions(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Integer status) {
        return Result.success(agentDefinitionService.listDefinitions(keyword, status));
    }

    @GetMapping("/{agentId}")
    public Result getDefinition(@PathVariable Integer agentId) {
        return Result.success(agentDefinitionService.getDefinition(agentId));
    }

    @PostMapping
    public Result createDefinition(@Valid @RequestBody AgentCreateRequest request) {
        return Result.success(agentDefinitionService.createDefinition(request));
    }

    @PutMapping("/{agentId}")
    public Result updateDefinition(@PathVariable Integer agentId,
                                   @Valid @RequestBody AgentCreateRequest request) {
        return Result.success(agentDefinitionService.updateDefinition(agentId, request));
    }

    @PutMapping("/{agentId}/status")
    public Result updateStatus(@PathVariable Integer agentId,
                               @RequestParam Integer status) {
        return Result.success(agentDefinitionService.updateStatus(agentId, status));
    }
}
