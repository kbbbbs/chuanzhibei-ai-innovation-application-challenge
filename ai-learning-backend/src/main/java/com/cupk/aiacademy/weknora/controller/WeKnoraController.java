package com.cupk.aiacademy.weknora.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/weknora")
public class WeKnoraController {

    @Autowired
    private WeKnoraService weKnoraService;

    @GetMapping("/knowledge-bases")
    public Result listKnowledgeBases() {
        return weKnoraService.listKnowledgeBases();
    }

    @PostMapping("/search")
    public Result search(@RequestBody WeKnoraSearchRequest request) {
        return weKnoraService.search(request);
    }

    @PostMapping("/sessions")
    public Result createSession(@RequestBody WeKnoraSessionRequest request) {
        return weKnoraService.createSession(request);
    }

    @GetMapping("/sessions")
    public Result listSessions() {
        return weKnoraService.listSessions();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result deleteSession(@PathVariable String sessionId) {
        return weKnoraService.deleteSession(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/stop")
    public Result stopSession(@PathVariable String sessionId) {
        return weKnoraService.stopSession(sessionId);
    }

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

    @PostMapping("/knowledge-bases/{kbId}/upload")
    public Result uploadFile(@PathVariable String kbId,
                              @RequestParam String fileName,
                              @RequestBody byte[] fileBytes) {
        return weKnoraService.uploadFile(kbId, fileBytes, fileName);
    }
}
