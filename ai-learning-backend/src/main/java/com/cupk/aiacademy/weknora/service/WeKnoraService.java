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

    SseEmitter knowledgeChat(String sessionId, String query, String knowledgeBaseIds);

    SseEmitter agentChat(String sessionId, String query, String agentId, String knowledgeBaseIds);

    Result listKnowledgeBases();

    Result uploadFile(String knowledgeBaseId, byte[] fileBytes, String fileName);

    /** 上传纯文本作为 .md 文件（用于 OCR 结果） */
    Result uploadKnowledgeText(String knowledgeBaseId, String fileName, String textContent);

    /** 创建知识库，返回包含 id 的 Result */
    Result createKnowledgeBase(String name, String description);

    /** 非流式聊天，收集完整响应后返回（用于需要完整JSON的场景） */
    String chatSync(String sessionId, String query);

    /** 删除知识库 */
    Result deleteKnowledgeBase(String kbId);

    /** 获取知识库中的文档列表 */
    Result listKnowledge(String kbId, int limit, int offset);

    /** 删除知识库中的文档 */
    Result deleteKnowledge(String knowledgeId);

    /** 更新知识库名称和描述 */
    Result updateKnowledgeBase(String kbId, String name, String description);

    /** 获取知识文件的内容片段 */
    Result getKnowledgeContent(String kbId, String knowledgeId);
}
