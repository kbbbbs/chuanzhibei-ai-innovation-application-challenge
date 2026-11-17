package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.assistant.AssistantLegacyAdapter;
import com.cupk.aiacademy.assistant.UnifiedAssistantService;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Old RAG chat URLs use the unified pipeline. Retrieval URLs remain independent. */
@RestController
@RequestMapping("/api/rag")
public class NativeRagChatController {
    private final UnifiedAssistantService assistant;
    private final AssistantLegacyAdapter legacy;
    public NativeRagChatController(UnifiedAssistantService assistant, AssistantLegacyAdapter legacy) {
        this.assistant = assistant;
        this.legacy = legacy;
    }

    @PostMapping("/chat")
    public Result chat(@RequestBody RagChatRequest request) {
        return Result.success(legacy.chat(session(request),request.query()));
    }
    @PostMapping("/chat/stream")
    public SseEmitter stream(@RequestBody RagChatRequest request) {
        return legacy.stream(session(request),request.query());
    }
    @PostMapping("/sessions")
    public Result create(@RequestBody Map<String,Object> request) {
        return Result.success(assistant.createSession(request));
    }
    @GetMapping("/sessions")
    public Result list() { return Result.success(assistant.sessions()); }
    @GetMapping("/sessions/{id}/messages")
    public Result messages(@PathVariable String id) {
        return Result.success(assistant.messages(assistant.legacySession(id)));
    }
    @DeleteMapping("/sessions/{id}")
    public Result delete(@PathVariable String id) {
        assistant.deleteSession(assistant.legacyOwnedSession(id));
        return Result.success();
    }

    private int session(RagChatRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) throw new BusinessException("请输入问题");
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            int id = assistant.legacySession(request.sessionId());
            assistant.assertRequestedScope(id,request.courseId(),request.lessonId(),request.knowledgeBaseIds());
            return id;
        }
        Map<String,Object> values = new LinkedHashMap<>();
        if (request.courseId() != null) values.put("courseId",request.courseId());
        if (request.lessonId() != null) values.put("lessonId",request.lessonId());
        if (request.knowledgeBaseIds() != null && !request.knowledgeBaseIds().isEmpty()) {
            if (request.knowledgeBaseIds().size() != 1) throw new BusinessException("请在课程内开始新对话");
            values.put("knowledgeBaseId",request.knowledgeBaseIds().getFirst());
        }
        return ((Number)assistant.createSession(values).get("id")).intValue();
    }
}
