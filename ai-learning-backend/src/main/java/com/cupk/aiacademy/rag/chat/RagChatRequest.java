package com.cupk.aiacademy.rag.chat;

import java.util.List;

public record RagChatRequest(List<String> knowledgeBaseIds, String query, String sessionId,
                             Integer courseId, Integer lessonId, String selectedText) {
    public RagChatRequest(List<String> knowledgeBaseIds, String query, String sessionId) {
        this(knowledgeBaseIds,query,sessionId,null,null,null);
    }
}
