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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class WeKnoraKnowledgeContextProvider implements KnowledgeContextProvider {
    private static final Logger log = LoggerFactory.getLogger(WeKnoraKnowledgeContextProvider.class);
    private static final Set<String> KNOWLEDGE_TEXT_FIELDS = Set.of(
            "content", "text", "answer", "chunk", "snippet", "summary", "title",
            "pagecontent", "documentcontent", "data", "result", "results"
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
            Result result = weKnoraService.search(new WeKnoraSearchRequest(knowledgeBaseIds, currentUserContent.trim()));
            if (result == null || !"200".equals(result.getCode()) || result.getData() == null) {
                return "";
            }
            return formatKnowledgeContext(result.getData());
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
                if (!item.isNull()) {
                    String id = item.asText();
                    if (!isBlank(id)) {
                        ids.add(id.trim());
                    }
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
        collectKnowledgeSnippets(root, null, snippets);
        if (snippets.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int maxCharacters = chatProperties.getKnowledgeContextMaxCharacters();
        for (int i = 0; i < snippets.size() && builder.length() < maxCharacters; i++) {
            String separator = builder.length() > 0 ? "\n" : "";
            String item = "[" + (i + 1) + "] " + snippets.get(i);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
