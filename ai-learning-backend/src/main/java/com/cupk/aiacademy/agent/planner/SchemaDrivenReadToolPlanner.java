package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SchemaDrivenReadToolPlanner {
    private static final Logger log = LoggerFactory.getLogger(SchemaDrivenReadToolPlanner.class);

    private final PlannerModelClient plannerModelClient;
    private final ObjectMapper objectMapper;
    private final AgentConfigurationParser configurationParser;
    private final AgentReadToolCatalog readToolCatalog;
    private final LlmReadToolDecisionParser parser;
    private final LlmReadToolDecisionValidator validator;
    private final AgentToolExecutor toolExecutor;
    private final AgentPlannerProperties properties;

    public SchemaDrivenReadToolPlanner(PlannerModelClient plannerModelClient,
                                       ObjectMapper objectMapper,
                                       AgentReadToolCatalog readToolCatalog,
                                       LlmReadToolDecisionParser parser,
                                       LlmReadToolDecisionValidator validator,
                                       AgentToolExecutor toolExecutor,
                                       AgentPlannerProperties properties) {
        this.plannerModelClient = plannerModelClient;
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

        return planAndExecute(userText.trim(), specs, false);
    }

    public String planSelectedToolContext(AgentDefinition agent,
                                          String userText,
                                          String toolName) {
        if (!properties.isEnabled() || agent == null || userText == null || userText.isBlank()) {
            return "";
        }
        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        AgentReadToolSpec selected = readToolCatalog.enabledSpecs(enabledTools).stream()
                .filter(spec -> normalize(spec.getToolName()).equals(normalize(toolName)))
                .findFirst()
                .orElseThrow(() -> new AgentPlanningException("读工具不存在或未启用：" + toolName));
        return planAndExecute(userText.trim(), List.of(selected), true);
    }

    private String planAndExecute(String userText,
                                  List<AgentReadToolSpec> specs,
                                  boolean selectedIntent) {
        LlmReadToolDecision decision = null;
        LlmReadToolValidationResult validation = null;
        String prompt = buildPlanPrompt(userText, specs, selectedIntent);
        int maxAttempts = Math.max(1, properties.getMaxRepairRounds() + 1);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                long startedNanos = System.nanoTime();
                decision = callModel(prompt);
                log.info("Agent planner stage completed: stage=read_parameters, model={}, attempt={}, promptChars={}, elapsedMs={}",
                        properties.getModel(), attempt + 1, prompt.length(), elapsedMillis(startedNanos));
                validation = validator.validate(decision, specs);
            } catch (AgentPlanningException e) {
                throw e;
            } catch (Exception e) {
                validation = LlmReadToolValidationResult.invalid(
                        List.of("模型输出无法解析：" + safeMessage(e)));
            }
            if (validation.isValid()) {
                break;
            }
            if (attempt + 1 < maxAttempts) {
                prompt = buildRepairPrompt(userText, specs, decision, validation.getErrors(), selectedIntent);
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
        return parser.parse(plannerModelClient.call(promptText, properties.getParameterTimeoutSeconds()));
    }

    private String buildPlanPrompt(String userText,
                                   List<AgentReadToolSpec> specs,
                                   boolean selectedIntent) {
        return """
                你是教学管理平台的读工具参数规划器。%s

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
                """.formatted(
                selectedIntent
                        ? "上一步提供了唯一候选读工具；相关时返回 TOOL 并提取参数，不相关时返回 CHAT 进行安全否决。"
                        : "判断是否读取平台数据。",
                userText,
                toJson(specs.stream().map(AgentReadToolSpec::toData).toList()));
    }

    private String buildRepairPrompt(String userText,
                                     List<AgentReadToolSpec> specs,
                                     LlmReadToolDecision previous,
                                     List<String> errors,
                                     boolean selectedIntent) {
        return """
                你上一次返回的读工具 JSON 未通过后端校验，请根据错误修正。
                只能返回修正后的 JSON；toolName 必须来自 readTools；arguments 必须严格匹配所选工具的 schema。
                %s

                用户输入：%s
                上一次输出：%s
                校验错误：%s
                readTools：%s
                """.formatted(
                selectedIntent ? "候选工具确实相关时返回 TOOL，不相关时可以返回 CHAT。" : "",
                userText, toJson(previous), toJson(errors),
                toJson(specs.stream().map(AgentReadToolSpec::toData).toList()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
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

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
