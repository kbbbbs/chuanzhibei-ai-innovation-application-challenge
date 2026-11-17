package com.cupk.aiacademy.assistant;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Adapts old response shapes to the shared, durable assistant request pipeline. */
@Component
public class AssistantLegacyAdapter {
    private final UnifiedAssistantService assistant;
    public AssistantLegacyAdapter(UnifiedAssistantService assistant) { this.assistant = assistant; }

    public Map<String,Object> chat(int sessionId, String content) {
        var submitted = assistant.send(sessionId, Map.of("requestId",UUID.randomUUID().toString(),"content",content));
        String requestId = submitted.get("requestId").toString();
        long deadline = System.nanoTime() + Duration.ofSeconds(185).toNanos();
        while (System.nanoTime() < deadline) {
            Map<String,Object> state = assistant.request(sessionId,requestId);
            String status = String.valueOf(state.get("status"));
            if ("COMPLETED".equals(status)) {
                Map<String,Object> result = new LinkedHashMap<>();
                Object payload = state.containsKey("message") ? state.get("message") : state.get("result");
                if (payload instanceof Map<?,?> map) map.forEach((key,value) -> result.put(key.toString(),value));
                result.put("sessionId",sessionId);
                result.put("answer",result.getOrDefault("content",""));
                return result;
            }
            if (!"RUNNING".equals(status) && !"QUEUED".equals(status))
                throw new BusinessException(String.valueOf(state.getOrDefault("error","回答已中断，请重新尝试")));
            try { Thread.sleep(150); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new BusinessException("等待回答已中断，可回到对话查看进度");
            }
        }
        throw new BusinessException("回答仍在处理中，请在智能助手查看进度");
    }

    public SseEmitter stream(int sessionId, String content) {
        var actor = UserContext.get();
        var emitter = new SseEmitter(190_000L);
        Thread.startVirtualThread(() -> {
            UserContext.set(actor);
            try {
                emitter.send(SseEmitter.event().name("progress").data(Map.of("message","正在处理问题…")));
                emitter.send(SseEmitter.event().name("complete").data(chat(sessionId,content)));
                emitter.send(SseEmitter.event().name("done").data(Map.of()));
                emitter.complete();
            } catch (Exception error) {
                try { emitter.send(SseEmitter.event().name("error").data(Map.of("message",error.getMessage() == null ? "回答失败，请重试" : error.getMessage()))); }
                catch (Exception disconnected) { /* The request remains available after disconnect. */ }
                emitter.complete();
            } finally { UserContext.clear(); }
        });
        return emitter;
    }
}
