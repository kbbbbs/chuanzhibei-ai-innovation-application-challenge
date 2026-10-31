package com.weknora.controller;

import com.weknora.common.R;
import com.weknora.dto.WeKnoraSearchRequest;
import com.weknora.dto.WeKnoraSessionRequest;
import com.weknora.service.WeKnoraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * WeKnora 集成 Controller
 * 所有接口路径以 /api/weknora 开头，前后端分离项目直接使用即可
 */
@RestController
@RequestMapping("/api/weknora")
public class WeKnoraController {

    @Autowired
    private WeKnoraService weKnoraService;

    // ==================== 知识库 ====================

    @GetMapping("/knowledge-bases")
    public R<?> listKnowledgeBases() {
        return weKnoraService.listKnowledgeBases();
    }

    // ==================== 知识检索 ====================

    @PostMapping("/search")
    public R<?> search(@RequestBody WeKnoraSearchRequest request) {
        return weKnoraService.search(request);
    }

    // ==================== 会话管理 ====================

    @PostMapping("/sessions")
    public R<?> createSession(@RequestBody WeKnoraSessionRequest request) {
        return weKnoraService.createSession(request);
    }

    @GetMapping("/sessions")
    public R<?> listSessions() {
        return weKnoraService.listSessions();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<?> deleteSession(@PathVariable String sessionId) {
        return weKnoraService.deleteSession(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/stop")
    public R<?> stopSession(@PathVariable String sessionId) {
        return weKnoraService.stopSession(sessionId);
    }

    // ==================== SSE 流式问答 ====================

    @PostMapping("/knowledge-chat/{sessionId}")
    public SseEmitter knowledgeChat(@PathVariable String sessionId,
                                     @RequestParam String query) {
        return weKnoraService.knowledgeChat(sessionId, query);
    }

    @PostMapping("/agent-chat/{sessionId}")
    public SseEmitter agentChat(@PathVariable String sessionId,
                                 @RequestParam String query,
                                 @RequestParam(required = false) String agentId) {
        return weKnoraService.agentChat(sessionId, query, agentId);
    }

    // ==================== 文件上传 ====================

    @PostMapping("/knowledge-bases/{kbId}/upload")
    public R<?> uploadFile(@PathVariable String kbId,
                            @RequestParam String fileName,
                            @RequestBody byte[] fileBytes) {
        return weKnoraService.uploadFile(kbId, fileBytes, fileName);
    }
}
