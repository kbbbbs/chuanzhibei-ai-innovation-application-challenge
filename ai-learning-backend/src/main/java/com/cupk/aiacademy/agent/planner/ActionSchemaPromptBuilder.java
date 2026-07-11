package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Data
public class ActionSchemaPromptBuilder {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE");

    private final ObjectMapper objectMapper;
    public String buildPlanPrompt(String userText,
                                  List<AgentActionSpec/*record*/> specs){
        return """
                你是系统操作规划器。你的任务是根据用户自然语言，从 actionSchemas 中选择一个系统支持的 action。

                严格要求：
                1. 只能返回 JSON 对象，不要 markdown，不要解释。
                2. decision 只能是 ACTION、ASK_USER、CHAT。
                3. 如果用户明确想执行系统支持的写操作，返回 decision=ACTION；ACTION 只表示生成待确认计划，不会直接执行。
                4. 明确写操作即使缺少必填参数，也优先返回 ACTION，并在 missingRequiredFields 写缺失字段；不要因为缺少 ID、教学班等参数就改成 CHAT。
                5. 只有无法判断要选择哪个 action，或多个 action 同时合理且无法取舍时，才返回 ASK_USER。
                6. 如果只是普通聊天或答疑，返回 decision=CHAT。
                7. actionCode 必须来自 actionSchemas。
                8. arguments 只能包含该 action 的 schema 中定义过的字段。
                9. 不要编造字段、ID 或业务数据，不要执行操作，只生成候选计划。
                10. 将“下周五晚上八点”等相对时间按当前时间换算成 schema 要求的绝对时间字符串。

                当前时间（Asia/Shanghai）：
                %s

                返回格式：
                {
                  "decision": "ACTION",
                  "actionCode": "course.create",
                  "arguments": {
                    "title": "Java 入门",
                    "categoryId": 1
                  },
                  "missingRequiredFields": [],
                  "userMessage": "",
                  "confidence": 0.9
                }

                用户输入：
                %s

                actionSchemas：
                %s
                """.formatted(currentTime(), userText, toJson(scopedSchemas(specs)));
    }

    public String buildRepairPrompt(String userText,
                                    List<AgentActionSpec> specs,
                                    LlmActionDecision previous,
                                    List<String> errors) {
        return """
                你上一次返回的 JSON 不符合系统约束。请根据错误信息修正。

                要求：
                1. 只能返回修正后的 JSON 对象。
                2. 不要解释。
                3. actionCode 必须来自 actionSchemas。
                4. arguments 不能包含未定义字段。
                5. 字段类型必须符合 schema。
                6. 如果用户明确想执行系统支持的写操作，保持 decision=ACTION；缺失参数写入 missingRequiredFields，不要改成 CHAT。
                7. 将相对时间按下面的当前时间换算成绝对时间字符串。

                当前时间（Asia/Shanghai）：
                %s

                用户输入：
                %s

                上一次输出：
                %s

                校验错误：
                %s

                actionSchemas：
                %s
                """.formatted(currentTime(), userText, toJson(previous), toJson(errors),
                toJson(scopedSchemas(specs)));
    }

    private List<Map<String, Object>> scopedSchemas(List<AgentActionSpec> specs) {
        if (specs == null) {
            return List.of();
        }
        return specs.stream().map(spec -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("actionCode", spec.actionCode());
            data.put("summary", spec.summary());
            data.put("parameters", spec.parameterSchema());
            data.put("requiredAnyOf", spec.requiredAnyOf());
            data.put("additionalProperties", false);
            return data;
        }).toList();
    }

    private String currentTime() {
        return ZonedDateTime.now(BUSINESS_ZONE).format(TIME_FORMATTER);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value); //value == null ? "null" : value.toString()
        }
    }
}
