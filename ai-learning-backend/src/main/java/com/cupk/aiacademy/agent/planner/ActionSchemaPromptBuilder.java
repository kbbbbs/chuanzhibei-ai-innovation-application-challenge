package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Component;
import java.util.*;

import static groovy.json.JsonOutput.toJson;

@Component
@Data
public class ActionSchemaPromptBuilder {
    private final ObjectMapper objectMapper;
    public String buildPlanPrompt(String userText,
                                  List<AgentActionSpec/*record*/> specs){
        return """
                你是系统操作规划器。你的任务是根据用户自然语言，从 actionSchemas 中选择一个系统支持的 action。

                严格要求：
                1. 只能返回 JSON 对象，不要 markdown，不要解释。
                2. decision 只能是 ACTION、ASK_USER、CHAT。
                3. 如果用户想执行系统操作，返回 decision=ACTION。
                4. 如果信息不足，返回 decision=ASK_USER，并在 missingRequiredFields 写缺失字段。
                5. 如果只是普通聊天或答疑，返回 decision=CHAT。
                6. actionCode 必须来自 actionSchemas。
                7. arguments 只能包含该 action 的 schema 中定义过的字段。
                8. 不要编造字段，不要执行操作，只生成候选计划。

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
                """.formatted(userText, toJson(specs.stream().map(AgentActionSpec::toData).toList()));
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

                用户输入：
                %s

                上一次输出：
                %s

                校验错误：
                %s

                actionSchemas：
                %s
                """.formatted(userText, toJson(previous), toJson(errors),
                toJson(specs.stream().map(AgentActionSpec::toData).toList()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value); //value == null ? "null" : value.toString()
        }
    }
}
