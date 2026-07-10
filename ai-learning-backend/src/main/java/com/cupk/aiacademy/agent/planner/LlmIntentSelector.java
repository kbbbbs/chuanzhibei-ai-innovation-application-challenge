package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LlmIntentSelector {
    private static final Logger log = LoggerFactory.getLogger(LlmIntentSelector.class);

    private final PlannerModelClient plannerModelClient;
    private final ObjectMapper objectMapper;
    private final AgentConfigurationParser configurationParser;
    private final AgentActionCatalog actionCatalog;
    private final AgentReadToolCatalog readToolCatalog;
    private final AgentPlannerProperties properties;

    public LlmIntentSelector(PlannerModelClient plannerModelClient,
                             ObjectMapper objectMapper,
                             AgentActionCatalog actionCatalog,
                             AgentReadToolCatalog readToolCatalog,
                             AgentPlannerProperties properties) {
        this.plannerModelClient = plannerModelClient;
        this.objectMapper = objectMapper;
        this.configurationParser = new AgentConfigurationParser(objectMapper);
        this.actionCatalog = actionCatalog;
        this.readToolCatalog = readToolCatalog;
        this.properties = properties;
    }

    public AgentIntentSelection select(AgentDefinition agent, String userText) {
        if (!properties.isEnabled() || agent == null || userText == null || userText.isBlank()) {
            return AgentIntentSelection.chat();
        }
        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        List<Map<String, Object>> writeActions = actionCatalog.enabledIntentSummaries(enabledTools);
        List<Map<String, Object>> readTools = readToolCatalog.enabledIntentSummaries(enabledTools);
        if (writeActions.isEmpty() && readTools.isEmpty()) {
            return AgentIntentSelection.chat();
        }

        String prompt = buildPrompt(userText.trim(), writeActions, readTools);
        List<String> errors = List.of();
        int maxAttempts = Math.max(1, properties.getMaxRepairRounds() + 1);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long startedNanos = System.nanoTime();
            String modelText = plannerModelClient.call(prompt);
            log.info("Agent planner stage completed: stage=intent_select, model={}, attempt={}, promptChars={}, elapsedMs={}",
                    properties.getModel(), attempt + 1, prompt.length(), elapsedMillis(startedNanos));
            LlmIntentDecision decision;
            try {
                decision = parse(modelText);
                errors = validate(decision, writeActions, readTools);
            } catch (Exception e) {
                decision = null;
                errors = List.of(safeMessage(e));
            }
            if (errors.isEmpty()) {
                AgentIntentSelection selection = toSelection(decision);
                log.info("Agent intent selected: type={}, code={}", selection.getType(), selection.getCode());
                return selection;
            }
            if (attempt + 1 < maxAttempts) {
                prompt = buildRepairPrompt(userText.trim(), writeActions, readTools, modelText, errors);
            }
        }
        throw new AgentPlanningException("无法可靠判断请求类型：" + String.join("；", errors));
    }

    private List<String> validate(LlmIntentDecision decision,
                                  List<Map<String, Object>> writeActions,
                                  List<Map<String, Object>> readTools) {
        if (decision == null) {
            return List.of("模型未返回意图决策");
        }
        List<String> errors = new ArrayList<>();
        String type = upper(decision.getDecision());
        if (!List.of("CHAT", "READ", "WRITE").contains(type)) {
            errors.add("decision 必须是 CHAT、READ 或 WRITE");
        }
        if (decision.getConfidence() == null || decision.getConfidence() < properties.getMinConfidence()) {
            errors.add("模型置信度不足，最低要求=" + properties.getMinConfidence());
        }
        String code = normalize(decision.getCode());
        if ("READ".equals(type) && !containsCode(readTools, code)) {
            errors.add("读工具不存在或未启用：" + decision.getCode());
        }
        if ("WRITE".equals(type) && !containsCode(writeActions, code)) {
            errors.add("写操作不存在或未启用：" + decision.getCode());
        }
        if ("CHAT".equals(type) && !code.isBlank()) {
            errors.add("CHAT 决策的 code 必须为空");
        }
        return List.copyOf(errors);
    }

    private AgentIntentSelection toSelection(LlmIntentDecision decision) {
        String code = normalize(decision.getCode());
        return switch (upper(decision.getDecision())) {
            case "READ" -> AgentIntentSelection.read(code);
            case "WRITE" -> AgentIntentSelection.write(code);
            default -> AgentIntentSelection.chat();
        };
    }

    private boolean containsCode(List<Map<String, Object>> summaries, String code) {
        for (Map<String, Object> summary : summaries) {
            if (code.equals(normalize(String.valueOf(summary.get("code"))))) {
                return true;
            }
        }
        return false;
    }

    private LlmIntentDecision parse(String modelText) throws Exception {
        int start = modelText.indexOf('{');
        int end = modelText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型返回中未找到 JSON 对象");
        }
        return objectMapper.readValue(modelText.substring(start, end + 1), LlmIntentDecision.class);
    }

    private String buildPrompt(String userText,
                               List<Map<String, Object>> writeActions,
                               List<Map<String, Object>> readTools) {
        return """
                你是教学管理平台的轻量意图选择器。只判断请求类型和目标代码，不提取业务参数。

                严格要求：
                1. 只返回一个 JSON 对象，不要解释或 markdown。
                2. decision 只能是 CHAT、READ、WRITE。
                3. 查询平台当前数据、列表、状态、数量或统计时选择 READ。
                4. 新增、修改、删除、发布、下架、批改等会改变平台数据的请求选择 WRITE。
                5. 普通聊天和知识问答选择 CHAT。
                6. READ 的 code 必须来自 readTools；WRITE 的 code 必须来自 writeActions；CHAT 的 code 必须为空字符串。
                7. 即使写操作缺少参数，也必须选择最匹配的 WRITE，参数将在下一阶段补充。
                8. 只有用户明确要求改变平台业务数据时才选择 WRITE；普通句子中出现“通过、回复、发布、删除”等词不代表业务操作。
                9. “请只回复：最终复测通过”“这个测试通过了吗”“解释删除和下架的区别”都属于 CHAT；“通过张三的教师申请”才属于 WRITE。

                返回格式：
                {"decision":"WRITE","code":"homework.create","confidence":0.95}

                用户输入：%s
                writeActions：%s
                readTools：%s
                """.formatted(userText, toJson(writeActions), toJson(readTools));
    }

    private String buildRepairPrompt(String userText,
                                     List<Map<String, Object>> writeActions,
                                     List<Map<String, Object>> readTools,
                                     String previous,
                                     List<String> errors) {
        return """
                上一次意图 JSON 未通过后端校验。只返回修正后的 JSON 对象。
                decision 只能是 CHAT、READ、WRITE；code 必须来自对应目录；CHAT 的 code 必须为空。

                用户输入：%s
                上一次输出：%s
                校验错误：%s
                writeActions：%s
                readTools：%s
                """.formatted(userText, previous, toJson(errors), toJson(writeActions), toJson(readTools));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AgentPlanningException("意图目录序列化失败", e);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage().trim();
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
