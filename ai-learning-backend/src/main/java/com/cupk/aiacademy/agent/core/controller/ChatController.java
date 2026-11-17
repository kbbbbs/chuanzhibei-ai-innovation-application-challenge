package com.cupk.aiacademy.agent.core.controller;

import com.cupk.aiacademy.assistant.AssistantLegacyAdapter;
import com.cupk.aiacademy.assistant.UnifiedAssistantService;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentSessionCreateRequest;
import com.cupk.aiacademy.dto.AgentSessionUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Compatibility routes share assistant sessions, authorization and generation. */
@RestController
public class ChatController {
    private final UnifiedAssistantService assistant;
    private final AssistantLegacyAdapter legacy;

    public ChatController(UnifiedAssistantService assistant, AssistantLegacyAdapter legacy) {
        this.assistant = assistant;
        this.legacy = legacy;
    }

    @Deprecated
    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        int id = ((Number)assistant.createSession(Map.of()).get("id")).intValue();
        return legacy.chat(id,message).get("content").toString();
    }

    @PostMapping("/api/agent/chat-once")
    public Result chatOnce(@Valid @RequestBody AgentChatRequest request) {
        int id = ((Number)assistant.createSession(Map.of()).get("id")).intValue();
        return Result.success(legacy.chat(id,request.getContent()));
    }

    @PostMapping("/api/agent/sessions")
    public Result createSession(@Valid @RequestBody(required=false) AgentSessionCreateRequest request) {
        return Result.success(assistant.createSession(request == null || request.getTitle() == null
                ? Map.of() : Map.of("title",request.getTitle())));
    }

    @GetMapping("/api/agent/sessions")
    public Result listSessions(@RequestParam(required=false) Integer userId) {
        return Result.success(assistant.sessions());
    }

    @PutMapping("/api/agent/sessions/{sessionId}")
    public Result updateSession(@PathVariable Integer sessionId,@Valid @RequestBody AgentSessionUpdateRequest request) {
        return Result.success(assistant.updateSession(sessionId,Map.of("title",request.getTitle())));
    }

    @DeleteMapping("/api/agent/sessions/{sessionId}")
    public Result deleteSession(@PathVariable Integer sessionId) {
        assistant.deleteSession(sessionId);
        return Result.success();
    }

    @GetMapping("/api/agent/sessions/{sessionId}/messages")
    public Result listMessages(@PathVariable Integer sessionId) {
        return Result.success(assistant.messages(sessionId));
    }

    @PostMapping("/api/agent/sessions/{sessionId}/chat")
    public Result chat(@PathVariable Integer sessionId,@Valid @RequestBody AgentChatRequest request) {
        return Result.success(legacy.chat(sessionId,request.getContent()));
    }
}
