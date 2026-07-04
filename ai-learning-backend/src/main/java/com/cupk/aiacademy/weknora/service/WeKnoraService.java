package com.cupk.aiacademy.weknora.service;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface WeKnoraService {

    Result search(WeKnoraSearchRequest request);

    Result createSession(WeKnoraSessionRequest request);

    Result listSessions();

    Result deleteSession(String sessionId);

    Result stopSession(String sessionId);

    SseEmitter knowledgeChat(String sessionId, String query);

    SseEmitter agentChat(String sessionId, String query, String agentId);

    Result listKnowledgeBases();

    Result uploadFile(String knowledgeBaseId, byte[] fileBytes, String fileName);

    /** 创建知识库，返回包含 id 的 Result */
    Result createKnowledgeBase(String name, String description);

    /** 非流式聊天，收集完整响应后返回（用于需要完整JSON的场景） */
    String chatSync(String sessionId, String query);
}
