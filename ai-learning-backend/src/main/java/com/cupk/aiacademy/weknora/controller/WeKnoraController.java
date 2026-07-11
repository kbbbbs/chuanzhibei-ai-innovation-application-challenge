package com.cupk.aiacademy.weknora.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.dto.WeKnoraChatRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/weknora")
public class WeKnoraController {

    @Autowired
    private WeKnoraService weKnoraService;

    @GetMapping("/knowledge-bases")
    public Result listKnowledgeBases() {
        return weKnoraService.listKnowledgeBases();
    }

    @PostMapping("/knowledge-bases")
    public Result createKnowledgeBase(@RequestBody Map<String, String> body) {
        return weKnoraService.createKnowledgeBase(
            body.getOrDefault("name", "未命名知识库"),
            body.getOrDefault("description", ""));
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
                                     @RequestParam(required = false) String query,
                                     @RequestBody(required = false) WeKnoraChatRequest body,
                                     @RequestParam(required = false) String knowledgeBaseIds) {
        String q = query;
        String kbIds = knowledgeBaseIds;
        if (body != null) {
            q = body.getQuery();
            if (body.getKnowledgeBaseIds() != null && !body.getKnowledgeBaseIds().isEmpty()) {
                kbIds = String.join(",", body.getKnowledgeBaseIds());
            }
        }
        return weKnoraService.knowledgeChat(sessionId, q, kbIds);
    }

    @PostMapping("/agent-chat/{sessionId}")
    public SseEmitter agentChat(@PathVariable String sessionId,
                                 @RequestParam(required = false) String query,
                                 @RequestParam(required = false) String agentId,
                                 @RequestBody(required = false) WeKnoraChatRequest body,
                                 @RequestParam(required = false) String knowledgeBaseIds) {
        String q = query;
        String aId = agentId;
        String kbIds = knowledgeBaseIds;
        if (body != null) {
            q = body.getQuery();
            if (body.getKnowledgeBaseIds() != null && !body.getKnowledgeBaseIds().isEmpty()) {
                kbIds = String.join(",", body.getKnowledgeBaseIds());
            }
        }
        return weKnoraService.agentChat(sessionId, q, aId, kbIds);
    }

    @PostMapping("/knowledge-bases/{kbId}/upload")
    public Result uploadFile(@PathVariable String kbId,
                              @RequestParam("file") MultipartFile file) {
        try {
            return weKnoraService.uploadFile(kbId, file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            return Result.error("读取文件失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/knowledge-bases/{kbId}")
    public Result deleteKnowledgeBase(@PathVariable String kbId) {
        return weKnoraService.deleteKnowledgeBase(kbId);
    }

    @GetMapping("/knowledge-bases/{kbId}/knowledge")
    public Result listKnowledge(@PathVariable String kbId,
                                 @RequestParam(defaultValue = "50") int limit,
                                 @RequestParam(defaultValue = "0") int offset) {
        return weKnoraService.listKnowledge(kbId, limit, offset);
    }

    @DeleteMapping("/knowledge/{knowledgeId}")
    public Result deleteKnowledge(@PathVariable String knowledgeId) {
        return weKnoraService.deleteKnowledge(knowledgeId);
    }

    @PutMapping("/knowledge-bases/{kbId}")
    public Result updateKnowledgeBase(@PathVariable String kbId,
                                       @RequestBody Map<String, String> body) {
        return weKnoraService.updateKnowledgeBase(kbId,
            body.getOrDefault("name", ""),
            body.getOrDefault("description", ""));
    }

    @GetMapping("/knowledge-bases/{kbId}/graph")
    public Result getKnowledgeGraph(@PathVariable String kbId) {
        return weKnoraService.getKnowledgeGraph(kbId);
    }

    @GetMapping("/knowledge-bases/{kbId}/wiki-page")
    public Result getWikiPage(@PathVariable String kbId,
                              @RequestParam String slug) {
        return weKnoraService.getWikiPage(kbId, slug);
    }

    @GetMapping("/knowledge-bases/{kbId}/knowledge/{knowledgeId}/content")
    public Result getKnowledgeContent(@PathVariable String kbId,
                                       @PathVariable String knowledgeId) {
        return weKnoraService.getKnowledgeContent(kbId, knowledgeId);
    }

    @GetMapping("/knowledge/{knowledgeId}/file")
    public void downloadFile(@PathVariable String knowledgeId,
                              HttpServletResponse response) {
        weKnoraService.downloadFile(knowledgeId, response);
    }

    @GetMapping("/local-file/**")
    public void proxyLocalFile(HttpServletRequest request,
                                HttpServletResponse response) {
        String path = request.getRequestURI().replace("/api/weknora/local-file/", "");
        weKnoraService.proxyLocalFile(path, response);
    }

    @GetMapping("/health")
    public Result healthCheck() {
        return weKnoraService.healthCheck();
    }
}
