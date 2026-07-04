package com.cupk.aiacademy.agent.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.agent.service.AgentService;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.dto.AgentSessionCreateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService agentService;
    private final AuthUtils authUtils;

    public AgentController(AgentService agentService, AuthUtils authUtils) {
        this.agentService = agentService;
        this.authUtils = authUtils;
    }

    @GetMapping
    public Result listAgents() {
        return Result.success(agentService.listAgents());
    }

    @PostMapping
    public Result createAgent(@RequestBody AgentCreateRequest request) {
        return Result.success(agentService.createAgent(request));
    }

    @GetMapping("/{id}")
    public Result getAgent(@PathVariable Integer id) {
        return Result.success(agentService.getAgent(id));
    }

    @PostMapping("/sessions")
    public Result createSession(@RequestBody AgentSessionCreateRequest request,
                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(agentService.createSession(request, currentUserId(authorization)));
    }

    @GetMapping("/sessions")
    public Result listSessions(@RequestParam(required = false) Integer userId,
                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        Integer currentUserId = currentUserId(authorization);
        return Result.success(agentService.listSessions(currentUserId == null ? userId : currentUserId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result listMessages(@PathVariable Integer sessionId) {
        return Result.success(agentService.listMessages(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/chat")
    public Result chat(@PathVariable Integer sessionId,
                       @RequestBody AgentChatRequest request) throws Exception {
        return Result.success(agentService.chat(sessionId, request));
    }

    @PostMapping(value = "/sessions/{sessionId}/stream", produces = "text/event-stream")
    public SseEmitter streamChat(@PathVariable Integer sessionId,
                                 @RequestBody AgentChatRequest request) {
        return agentService.streamChat(sessionId, request);
    }

    private Integer currentUserId(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authUtils.requireLogin(authorization).getUserId();
    }
}
