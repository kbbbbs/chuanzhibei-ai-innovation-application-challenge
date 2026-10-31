package com.cupk.aiacademy.rag.chat;

import java.util.List;

public record RagChatRequest(List<String> knowledgeBaseIds, String query, String sessionId) {}
