package com.cupk.aiacademy.rag.graph;

import java.util.List;
import java.util.Map;

public record GraphSlice(
        List<Map<String, Object>> nodes, List<Map<String, Object>> edges, boolean truncated) {}
