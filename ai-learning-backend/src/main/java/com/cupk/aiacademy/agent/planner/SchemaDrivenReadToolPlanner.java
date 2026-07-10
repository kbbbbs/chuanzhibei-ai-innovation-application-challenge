package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SchemaDrivenReadToolPlanner {
    private static final Logger log = LoggerFactory.getLogger(SchemaDrivenReadToolPlanner.class);

    private final ChatModelClient chatModelClient;
    private final ObjectMapper objectMapper;
    private final AgentConfigurationParser configurationParser;
    private final AgentReadToolCatalog readToolCatalog;
    private final LlmReadToolDecisionParser parser;
    private final LlmReadToolDecisionValidator validator;
    private final AgentToolExecutor toolExecutor;
    private final AgentPlannerProperties properties;

    public SchemaDrivenReadToolPlanner(ChatModelClient chatModelClient,
                                       ObjectMapper objectMapper,
                                       AgentReadToolCatalog readToolCatalog,
                                       LlmReadToolDecisionParser parser,
                                       LlmReadToolDecisionValidator validator,
                                       AgentToolExecutor toolExecutor,
                                       AgentPlannerProperties properties) {
        this.chatModelClient = chatModelClient;
        this.objectMapper = objectMapper;
        this.configurationParser = new AgentConfigurationParser(objectMapper);
        this.readToolCatalog = readToolCatalog;
        this.parser = parser;
        this.validator = validator;
        this.toolExecutor = toolExecutor;
        this.properties = properties;
    }

    public String planToolContext(AgentDefinition agent, String userText) {
        if (!properties.isEnabled() || agent == null || userText == null || userText.isBlank()) {
            return "";
        }
        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        List<AgentReadToolSpec> specs = readToolCatalog.enabledSpecs(enabledTools);
        if (specs.isEmpty()) {
            return "";
        }

        LlmReadToolDecision decision = null;
        LlmReadToolValidationResult validation = null;
        String prompt = buildPlanPrompt(userText.trim(), specs);
        int maxAttempts = Math.max(2, properties.getMaxRepairRounds() + 1);
        int chatConfirmations = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                decision = callModel(prompt);
                validation = validator.validate(decision, specs);
            } catch (Exception e) {
                validation = LlmReadToolValidationResult.invalid(
                        List.of("模型输出无法解析：" + safeMessage(e)));
            }
            if (validation.isValid()) {
                if (!validation.isChat()) {
                    break;
                }
                chatConfirmations++;
                if (chatConfirmations >= 2 || attempt + 1 >= maxAttempts) {
                    break;
                }
                prompt = buildChatVerificationPrompt(userText.trim(), specs, decision);
                continue;
            }
            chatConfirmations = 0;
            if (attempt + 1 < maxAttempts) {
                prompt = buildRepairPrompt(userText.trim(), specs, decision, validation.getErrors());
            }
        }

        if (validation == null || !validation.isValid()) {
            log.warn("Agent read planner did not produce a valid tool decision: errors={}",
                    validation == null ? List.of("missing validation result") : validation.getErrors());
            List<String> errors = validation == null
                    ? List.of("模型未返回可校验的读工具决策")
                    : validation.getErrors();
            return "[read_planner]\n未执行平台数据库查询；读工具规划在限定轮次内仍未通过后端校验；"
                    + "校验错误=" + String.join("；", errors)
                    + "。回答时必须明确说明尚未查询到平台数据，并请用户补充更明确的查询对象或条件。";
        }
        if (validation.isChat()) {
            log.info("Agent read planner selected CHAT");
            return "";
        }
        try {
            log.info("Agent read planner selected tool: {}", validation.getToolName());
            Optional<AgentToolResult> result = toolExecutor.executeReadTool(
                    validation.getToolName(),
                    validation.getNormalizedArguments()
            );
            if (result.isEmpty() || !result.get().hasContent()) {
                return "[" + validation.getToolName() + "]\n工具未返回数据。";
            }
            return "[" + validation.getToolName() + "]\n" + result.get().content();
        } catch (Exception e) {
            log.warn("Model-selected read tool failed: tool={}, message={}",
                    validation.getToolName(), e.getMessage(), e);
            return "[" + validation.getToolName() + "]\n工具执行失败：" + safeMessage(e);
        }
    }

    private LlmReadToolDecision callModel(String promptText) {
        ChatResponse response = chatModelClient.call(new Prompt(List.of(new UserMessage(promptText))));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalArgumentException("模型未返回读工具决策");
        }
        return parser.parse(response.getResult().getOutput().getContent());
    }

    private String buildPlanPrompt(String userText, List<AgentReadToolSpec> specs) {
        return """
                你是教学管理平台的读工具规划器。根据用户输入决定是否需要读取平台数据。

                严格要求：
                1. 只返回一个 JSON 对象，不要解释或 markdown。
                2. decision 只能是 TOOL 或 CHAT。
                3. 查询平台当前数据时返回 TOOL；普通聊天、知识问答或写操作返回 CHAT。
                4. TOOL 时只能选择 readTools 中的一个 toolName。
                5. arguments 必须严格匹配所选工具在 readTools 中声明的 arguments schema，禁止未知字段。
                6. 只填写用户明确提供或可直接归一化的条件，不得编造实体、ID、状态或过滤结果。
                7. 对 query、keyword、名称类字段只做简洁整理，保留用户明确给出的实体名称。
                8. 每次最多选择一个最匹配的专用读工具；没有合适工具时返回 CHAT。

                返回格式：
                {"decision":"TOOL","toolName":"course_search","arguments":{"query":"Java 入门课程","maxItems":10},"userMessage":"","confidence":0.95}

                用户输入：
                %s

                readTools：
                %s
                """.formatted(userText, toJson(specs.stream().map(AgentReadToolSpec::toData).toList()));
    }

    private String buildRepairPrompt(String userText,
                                     List<AgentReadToolSpec> specs,
                                     LlmReadToolDecision previous,
                                     List<String> errors) {
        return """
                你上一次返回的读工具 JSON 未通过后端校验，请根据错误修正。
                只能返回修正后的 JSON；toolName 必须来自 readTools；arguments 必须严格匹配所选工具的 schema。

                用户输入：%s
                上一次输出：%s
                校验错误：%s
                readTools：%s
                """.formatted(userText, toJson(previous), toJson(errors),
                toJson(specs.stream().map(AgentReadToolSpec::toData).toList()));
    }

    private String buildChatVerificationPrompt(String userText,
                                               List<AgentReadToolSpec> specs,
                                               LlmReadToolDecision previous) {
        return """
                你上一次将请求判断为 CHAT。请在最终确认前重新逐项检查 readTools。
                查询平台当前数据、列表、状态、数量、统计、记录或业务实体时必须返回 TOOL；
                只有普通聊天、通用知识问答或写操作才返回 CHAT。
                只返回一个 JSON 对象；TOOL 的 arguments 必须严格匹配所选工具 schema。

                用户输入：%s
                上一次输出：%s
                readTools：%s
                """.formatted(userText, toJson(previous),
                toJson(specs.stream().map(AgentReadToolSpec::toData).toList()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "未知错误";
        }
        return exception.getMessage().trim();
    }
}
