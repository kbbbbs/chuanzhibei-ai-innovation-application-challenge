package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "weknora.enabled", havingValue = "true")
public class WeKnoraKnowledgeContextProvider implements KnowledgeContextProvider {
    private static final Logger log = LoggerFactory.getLogger(WeKnoraKnowledgeContextProvider.class);
    private static final Set<String> KNOWLEDGE_TEXT_FIELDS = Set.of(
            "content", "text", "answer", "chunk", "snippet", "summary", "title",
            "pagecontent", "documentcontent", "data", "result", "results"
    );
    private static final List<String> KNOWLEDGE_CONTENT_FIELDS = List.of(
            "content", "text", "chunk", "snippet", "summary", "answer"
    );
    private static final List<String> KNOWLEDGE_SOURCE_FIELDS = List.of(
            "knowledge_title", "knowledge_filename", "file_name", "title", "name"
    );

    private final AgentChatProperties chatProperties;
    private final ObjectMapper objectMapper;
    private final WeKnoraService weKnoraService;

    public WeKnoraKnowledgeContextProvider(AgentChatProperties chatProperties,
                                           ObjectMapper objectMapper,
                                           WeKnoraService weKnoraService) {
        this.chatProperties = chatProperties;
        this.objectMapper = objectMapper;
        this.weKnoraService = weKnoraService;
    }

    @Override
    public String retrieve(AgentDefinition agent, String currentUserContent) {
        List<String> knowledgeBaseIds = parseKnowledgeBaseIds(agent.getKnowledgeBaseIds());
        if (knowledgeBaseIds.isEmpty()
                || chatProperties.getKnowledgeContextMaxSnippets() <= 0
                || chatProperties.getKnowledgeContextMaxCharacters() <= 0) {
            return "";
        }

        try {
            Result result = weKnoraService.search(new WeKnoraSearchRequest(knowledgeBaseIds, currentUserContent.trim(), null, null));
            if (result == null || !"200".equals(result.getCode()) || result.getData() == null) {
                return "";
            }
            String context = formatKnowledgeContext(result.getData());
            if (!isBlank(context)) {
                log.info("Agent RAG context prepared: agentCode={}, knowledgeBaseCount={}, contextCharacters={}",
                        agent.getCode(), knowledgeBaseIds.size(), context.length());
            }
            return context;
        } catch (Exception e) {
            log.warn("Agent knowledge search failed, continue without knowledge context", e);
            return "";
        }
    }

    private List<String> parseKnowledgeBaseIds(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value.trim());
            if (!node.isArray()) {
                return List.of();
            }

            List<String> ids = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    log.warn("Ignore non-string WeKnora knowledge base id: {}", item);
                    continue;
                }
                String id = normalizeUuid(item.asText());
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (JsonProcessingException e) {
            log.warn("Invalid knowledgeBaseIds stored in Agent definition: {}", value);
            return List.of();
        }
    }

    private String formatKnowledgeContext(Object searchData) {
        JsonNode root = objectMapper.valueToTree(searchData);
        List<String> snippets = new ArrayList<>();
        collectKnowledgeHits(root, snippets);
        if (snippets.isEmpty()) {
            collectKnowledgeSnippets(root, null, snippets);
        }
        if (snippets.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int maxCharacters = chatProperties.getKnowledgeContextMaxCharacters();
        int maxSnippets = Math.max(1, chatProperties.getKnowledgeContextMaxSnippets());
        int maxCharactersPerSnippet = Math.max(300, maxCharacters / maxSnippets);
        for (int i = 0; i < snippets.size() && builder.length() < maxCharacters; i++) {
            String separator = builder.length() > 0 ? "\n" : "";
            String item = "[" + (i + 1) + "] " + snippets.get(i);
            if (item.length() > maxCharactersPerSnippet) {
                item = item.substring(0, maxCharactersPerSnippet) + "…";
            }
            int remaining = maxCharacters - builder.length() - separator.length();
            if (remaining <= 0) {
                break;
            }
            if (item.length() > remaining) {
                item = item.substring(0, remaining);
            }
            builder.append(separator).append(item);
        }
        return builder.toString();
    }

    private void collectKnowledgeHits(JsonNode node, List<String> snippets) {
        if (node == null || node.isNull() || snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectKnowledgeHits(child, snippets);
                if (snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
                    break;
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String content = firstText(node, KNOWLEDGE_CONTENT_FIELDS);
        if (!isBlank(content)) {
            String source = firstText(node, KNOWLEDGE_SOURCE_FIELDS);
            String formatted = isBlank(source)
                    ? content
                    : "来源：" + source + "\n" + content;
            addKnowledgeSnippet(formatted, snippets);
            return;
        }

        for (Map.Entry<String, JsonNode> field : node.properties()) {
            collectKnowledgeHits(field.getValue(), snippets);
            if (snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
                break;
            }
        }
    }

    private String firstText(JsonNode node, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isValueNode() && !isBlank(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private void collectKnowledgeSnippets(JsonNode node, String fieldName, List<String> snippets) {
        if (node == null || node.isNull() || snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectKnowledgeSnippets(child, fieldName, snippets);
            }
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                collectKnowledgeSnippets(field.getValue(), field.getKey(), snippets);
                if (snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
                    break;
                }
            }
            return;
        }
        if (node.isValueNode() && shouldCollectKnowledgeField(fieldName)) {
            addKnowledgeSnippet(node.asText(), snippets);
        }
    }

    private boolean shouldCollectKnowledgeField(String fieldName) {
        if (fieldName == null) {
            return true;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return KNOWLEDGE_TEXT_FIELDS.contains(normalized);
    }

    private void addKnowledgeSnippet(String value, List<String> snippets) {
        String snippet = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (isBlank(snippet) || snippets.contains(snippet)) {
            return;
        }
        snippets.add(snippet);
    }

    private String normalizeUuid(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            String id = value.trim();
            String normalized = UUID.fromString(id).toString();
            if (!normalized.equalsIgnoreCase(id)) {
                log.warn("Ignore non-canonical WeKnora knowledge base id: {}", value);
                return null;
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            log.warn("Ignore invalid WeKnora knowledge base id: {}", value);
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
