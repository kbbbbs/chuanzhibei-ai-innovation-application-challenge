package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record RagSearchRequest(
        @JsonAlias("knowledgeBaseIds") List<String> kbIds,
        String query,
        @JsonAlias("topK") Integer limit) { }
