package com.cupk.aiacademy.agent.controller;

import com.cupk.aiacademy.agent.service.AgentChatService;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentSessionCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChatController {

    private final AgentChatService agentChatService;

    public ChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        if (!StringUtils.hasText(message)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "信息不能为空");
        }
        return agentChatService.chatOnce(message);
    }

    @PostMapping("/api/agent/sessions")
    public Result createSession(@Valid @RequestBody(required = false) AgentSessionCreateRequest request) {
        return Result.success(agentChatService.createSession(request));
    }

    @GetMapping("/api/agent/sessions")
    public Result listSessions(@RequestParam(required = false) Integer userId) {
        return Result.success(agentChatService.listSessions(userId));
    }

    @GetMapping("/api/agent/sessions/{sessionId}/messages")
    public Result listMessages(@PathVariable Integer sessionId) {
        return Result.success(agentChatService.listMessages(sessionId));
    }

    @PostMapping("/api/agent/sessions/{sessionId}/chat")
    public Result chat(@PathVariable Integer sessionId,
                       @Valid @RequestBody AgentChatRequest request) {
        return Result.success(agentChatService.chat(sessionId, request));
    }
}
