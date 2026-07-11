package com.cupk.aiacademy.weknora.service;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /** 获取知识库图谱配置 */
    Result getKnowledgeGraph(String kbId);

    /** 获取 WeKnora Wiki 页面内容 */
    Result getWikiPage(String kbId, String slug);

    /** 获取知识文件的内容片段 */
    Result getKnowledgeContent(String kbId, String knowledgeId);

    /** 下载/查看知识文件原始内容 */
    void downloadFile(String knowledgeId, HttpServletResponse response);

    /** 代理 WeKnora 本地文件（用于渲染 local:// 图片引用） */
    void proxyLocalFile(String path, HttpServletResponse response);

    /** 健康检查：快速检测 WeKnora 服务是否可达 */
    Result healthCheck();
}
