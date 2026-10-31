package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class NativeRagChatController {
    private final NativeRagChatService chat;
    public NativeRagChatController(NativeRagChatService chat) { this.chat = chat; }

    @PostMapping("/chat")
    public Result chat(@RequestBody RagChatRequest request) { return Result.success(chat.chat(request)); }
    @PostMapping("/chat/stream")
    public SseEmitter stream(@RequestBody RagChatRequest request) { return chat.stream(request); }
    @PostMapping("/sessions")
    public Result create(@RequestBody Map<String, String> request) {
        return Result.success(chat.createSession(request.get("knowledgeBaseId"), request.get("title")));
    }
    @GetMapping("/sessions")
    public Result list() { return Result.success(chat.listSessions()); }
    @GetMapping("/sessions/{id}/messages")
    public Result messages(@PathVariable String id) { return Result.success(chat.messages(id)); }
    @DeleteMapping("/sessions/{id}")
    public Result delete(@PathVariable String id) { chat.deleteSession(id); return Result.success(); }
}
