package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentActionSelectionService {
    private static final Logger log = LoggerFactory.getLogger(AgentActionSelectionService.class);
    private static final double MIN_CONFIDENCE = 0.60;

    private final ObjectMapper objectMapper;
    private final ChatModelClient chatModelClient;
    private final AgentActionCatalog actionCatalog;

    @Autowired
    public AgentActionSelectionService(ObjectMapper objectMapper,
                                       ObjectProvider<ChatModelClient> chatModelClientProvider,
                                       AgentActionCatalog actionCatalog) {
        this(objectMapper,
                chatModelClientProvider == null ? null : chatModelClientProvider.getIfAvailable(),
                actionCatalog);
    }

    AgentActionSelectionService(ObjectMapper objectMapper,
                                ChatModelClient chatModelClient,
                                AgentActionCatalog actionCatalog) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.chatModelClient = chatModelClient;
        this.actionCatalog = actionCatalog;
    }

    public Optional<AgentActionSelectionResult> select(List<String> enabledTools, String rawRequest) {
        if (chatModelClient == null || actionCatalog == null || isBlank(rawRequest)) {
            return Optional.empty();
        }
        List<Map<String, Object>> actions = actionCatalog.enabledActionSummaries(enabledTools);
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        try {
            ChatResponse response = chatModelClient.call(buildPrompt(actions, rawRequest));
            AgentActionSelectionResult result = parseResult(response, enabledTools);
            if (result.needsClarification() || result.confidence() < MIN_CONFIDENCE || result.toolName().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("Agent action model selection failed: message={}", e.getMessage());
            return Optional.empty();
        }
    }

    private Prompt buildPrompt(List<Map<String, Object>> actions, String rawRequest) {
        String userPrompt = """
                用户原话：%s

                已启用动作：
                %s

                返回 JSON 格式：
                {"actionCode":"course.create","confidence":0.0,"needsClarification":false}
                """.formatted(rawRequest, toJson(actions));
        return new Prompt(List.of(
                new SystemMessage("""
                        你是教学管理系统的写操作意图选择器。只返回一个 JSON 对象，不要 Markdown，不要解释。
                        只能从已启用动作的 actionCode 中选择一个；不要创造动作。
                        如果用户没有表达清楚的写操作，或多个动作都可能成立，返回 actionCode 为空、confidence 为 0、needsClarification 为 true。
                        你只选择动作，不抽取参数，不判断数据库是否存在。
                        """),
                new UserMessage(userPrompt)
        ));
    }

    private AgentActionSelectionResult parseResult(ChatResponse response, List<String> enabledTools) throws Exception {
        String content = assistantContent(response);
        if (isBlank(content)) {
            return new AgentActionSelectionResult("", "", 0, true);
        }
        JsonNode root = parseJsonObject(content);
        String actionCode = text(root.get("actionCode"));
        double confidence = root.has("confidence") && root.get("confidence").isNumber()
                ? root.get("confidence").asDouble()
                : 0;
        boolean needsClarification = root.has("needsClarification") && root.get("needsClarification").asBoolean(false);
        String toolName = actionCatalog.findEnabledToolNameByActionCode(enabledTools, actionCode).orElse("");
        return new AgentActionSelectionResult(actionCode, toolName, confidence, needsClarification);
    }

    private String assistantContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String content = response.getResult().getOutput().getContent();
        return content == null ? "" : content.trim();
    }

    private JsonNode parseJsonObject(String content) throws Exception {
        String json = stripCodeFence(content);
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (Exception ignored) {
            // Fall through to extracting the first object from surrounding text.
        }
        String objectText = firstJsonObject(json);
        if (objectText == null) {
            throw new IllegalArgumentException("模型未返回 JSON 对象");
        }
        JsonNode node = objectMapper.readTree(objectText);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("模型返回的 JSON 不是对象");
        }
        return node;
    }

    private String stripCodeFence(String content) {
        String text = content == null ? "" : content.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        text = text.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        text = text.replaceFirst("\\s*```$", "");
        return text.trim();
    }

    private String firstJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
