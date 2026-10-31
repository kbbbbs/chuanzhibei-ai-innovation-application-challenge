package com.weknora.service;

import com.weknora.common.R;
import com.weknora.dto.WeKnoraSearchRequest;
import com.weknora.dto.WeKnoraSessionRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface WeKnoraService {

    /** 知识检索 */
    R<?> search(WeKnoraSearchRequest request);

    /** 创建会话 */
    R<?> createSession(WeKnoraSessionRequest request);

    /** 获取会话列表 */
    R<?> listSessions();

    /** 删除会话 */
    R<?> deleteSession(String sessionId);

    /** 停止会话 */
    R<?> stopSession(String sessionId);

    /** 知识问答（SSE 流式） */
    SseEmitter knowledgeChat(String sessionId, String query);

    /** Agent 问答（SSE 流式） */
    SseEmitter agentChat(String sessionId, String query, String agentId);

    /** 获取知识库列表 */
    R<?> listKnowledgeBases();

    /** 上传文档到知识库 */
    R<?> uploadFile(String knowledgeBaseId, byte[] fileBytes, String fileName);
}
