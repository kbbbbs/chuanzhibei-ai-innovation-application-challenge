package com.cupk.aiacademy.rag.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/** Either knowledge bases the user may read, or a course (and lesson) whose granted materials are searched. */
public record RagChatRequest(List<String> knowledgeBaseIds, String query, String sessionId, Integer courseId, Integer lessonId) {
    @JsonCreator public RagChatRequest {}
    public RagChatRequest(List<String> knowledgeBaseIds, String query, String sessionId) {
        this(knowledgeBaseIds, query, sessionId, null, null);
    }
}
