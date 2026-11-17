package com.cupk.aiacademy.assistant;

import com.cupk.aiacademy.common.Result;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class UnifiedAssistantController {
    private final UnifiedAssistantService assistant;

    public UnifiedAssistantController(UnifiedAssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/sessions")
    public Result create(@RequestBody Map<String, Object> input) {
        return Result.success(assistant.createSession(input));
    }

    @GetMapping("/sessions")
    public Result sessions() {
        return Result.success(assistant.sessions());
    }

    @PutMapping("/sessions/{id}")
    public Result rename(@PathVariable int id, @RequestBody Map<String, Object> input) {
        return Result.success(assistant.updateSession(id, input));
    }

    @DeleteMapping("/sessions/{id}")
    public Result delete(@PathVariable int id) {
        assistant.deleteSession(id);
        return Result.success();
    }

    @GetMapping("/sessions/{id}/messages")
    public Result messages(@PathVariable int id) {
        return Result.success(assistant.messages(id));
    }

    @PostMapping("/sessions/{id}/messages")
    public Result send(@PathVariable int id, @RequestBody Map<String, Object> input) {
        return Result.success(assistant.send(id, input));
    }

    @GetMapping("/sessions/{id}/requests/{requestId}")
    public Result request(@PathVariable int id, @PathVariable String requestId) {
        return Result.success(assistant.request(id, requestId));
    }

    @PostMapping("/sessions/{id}/requests/{requestId}/cancel")
    public Result cancel(@PathVariable int id, @PathVariable String requestId) {
        return Result.success(assistant.cancel(id, requestId));
    }

    @PostMapping("/sessions/{id}/stream")
    public SseEmitter stream(
            @PathVariable int id,
            @RequestBody Map<String, Object> input,
            HttpServletResponse response) {
        streamHeaders(response);
        return assistant.stream(id, input);
    }

    @GetMapping("/sessions/{id}/requests/{requestId}/events")
    public SseEmitter events(
            @PathVariable int id, @PathVariable String requestId, HttpServletResponse response) {
        streamHeaders(response);
        return assistant.events(id, requestId);
    }

    private void streamHeaders(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-store");
    }
}
