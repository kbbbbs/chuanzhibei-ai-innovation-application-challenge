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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentParameterExtractor {
    private static final Logger log = LoggerFactory.getLogger(AgentParameterExtractor.class);

    private final ObjectMapper objectMapper;
    private final ChatModelClient chatModelClient;
    private final AgentActionSchemaValidator schemaValidator;

    @Autowired
    public AgentParameterExtractor(ObjectMapper objectMapper,
                                   ObjectProvider<ChatModelClient> chatModelClientProvider,
                                   AgentActionSchemaValidator schemaValidator) {
        this(objectMapper,
                chatModelClientProvider == null ? null : chatModelClientProvider.getIfAvailable(),
                schemaValidator);
    }

    public AgentParameterExtractor(ObjectMapper objectMapper) {
        this(objectMapper, (ChatModelClient) null, new AgentActionSchemaValidator());
    }

    AgentParameterExtractor(ObjectMapper objectMapper, ChatModelClient chatModelClient) {
        this(objectMapper, chatModelClient, new AgentActionSchemaValidator());
    }

    AgentParameterExtractor(ObjectMapper objectMapper,
                            ChatModelClient chatModelClient,
                            AgentActionSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.chatModelClient = chatModelClient;
        this.schemaValidator = schemaValidator == null ? new AgentActionSchemaValidator() : schemaValidator;
    }

    public AgentParameterExtractionResult extract(AgentActionSpec spec, String rawRequest) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        boolean modelAttempted = false;
        boolean modelSucceeded = false;
        boolean modelRepaired = false;

        if (shouldUseModel(spec, rawRequest)) {
            modelAttempted = true;
            try {
                parameters.putAll(extractWithModel(spec, rawRequest));
                modelSucceeded = !parameters.isEmpty();
            } catch (Exception e) {
                log.warn("Agent parameter model extraction failed: actionCode={}, message={}",
                        spec == null ? "" : spec.actionCode(),
                        e.getMessage());
            }
        }

        Map<String, Object> fallbackParameters = inferWithLocalFallback(spec, rawRequest);
        fallbackParameters.forEach(parameters::putIfAbsent);

        AgentActionParameterValidationResult validation = schemaValidator.validate(spec, parameters);
        if (modelAttempted && !validation.issues().isEmpty()) {
            try {
                Map<String, Object> repaired = repairWithModel(spec, rawRequest, parameters, validation.issues());
                if (!repaired.isEmpty()) {
                    fallbackParameters.forEach(repaired::putIfAbsent);
                    AgentActionParameterValidationResult repairedValidation = schemaValidator.validate(spec, repaired);
                    if (repairedValidation.issues().size() < validation.issues().size()) {
                        validation = repairedValidation;
                        modelSucceeded = true;
                        modelRepaired = true;
                    }
                }
            } catch (Exception e) {
                log.warn("Agent parameter model repair failed: actionCode={}, message={}",
                        spec == null ? "" : spec.actionCode(),
                        e.getMessage());
            }
        }

        String source = modelRepaired ? "model_repair"
                : (modelSucceeded ? "model" : (validation.parameters().isEmpty() ? "none" : "fallback"));
        return new AgentParameterExtractionResult(
                validation.parameters(),
                validation.missingRequiredFields(),
                modelAttempted,
                modelSucceeded,
                source,
                validation.issues()
        );
    }

    private boolean shouldUseModel(AgentActionSpec spec, String rawRequest) {
        return chatModelClient != null
                && spec != null
                && !isBlank(spec.actionCode())
                && !isBlank(rawRequest);
    }

    private Map<String, Object> extractWithModel(AgentActionSpec spec, String rawRequest) throws Exception {
        ChatResponse response = chatModelClient.callPlanning(buildExtractionPrompt(spec, rawRequest));
        String content = assistantContent(response);
        if (isBlank(content)) {
            return Map.of();
        }
        JsonNode root = parseJsonObject(content);
        JsonNode arguments = root.has("arguments") ? root.get("arguments") : root;
        if (arguments == null || !arguments.isObject()) {
            return Map.of();
        }
        return argumentsToMap(arguments);
    }

    private Map<String, Object> repairWithModel(AgentActionSpec spec,
                                                String rawRequest,
                                                Map<String, Object> invalidArguments,
                                                List<AgentActionValidationIssue> issues) throws Exception {
        ChatResponse response = chatModelClient.callPlanning(buildRepairPrompt(spec, rawRequest, invalidArguments, issues));
        String content = assistantContent(response);
        if (isBlank(content)) {
            return Map.of();
        }
        JsonNode root = parseJsonObject(content);
        JsonNode arguments = root.has("arguments") ? root.get("arguments") : root;
        if (arguments == null || !arguments.isObject()) {
            return Map.of();
        }
        return argumentsToMap(arguments);
    }

    private Prompt buildExtractionPrompt(AgentActionSpec spec, String rawRequest) {
        StringBuilder fields = new StringBuilder();
        for (AgentActionParameterSpec parameter : spec.parameters()) {
            if (!fields.isEmpty()) {
                fields.append("\n");
            }
            fields.append("- name=").append(parameter.name())
                    .append("; label=").append(parameter.label())
                    .append("; required=").append(parameter.required())
                    .append("; type=").append(parameterType(parameter));
            if (!parameter.enumValues().isEmpty()) {
                fields.append("; enum=").append(parameter.enumValues());
            }
            if (!parameter.aliases().isEmpty()) {
                fields.append("; aliases=").append(parameter.aliases());
            }
            if (!parameter.description().isBlank()) {
                fields.append("; description=").append(parameter.description());
            }
        }

        String userPrompt = """
                动作编码：%s
                动作名称：%s
                用户原话：%s

                可抽取字段：
                %s

                JSON Schema：
                %s

                必填约束：
                %s

                返回 JSON 格式：
                {"arguments":{"fieldName":"value"},"missingRequiredFields":["fieldName"],"confidence":0.0}
                """.formatted(spec.actionCode(), spec.actionName(), rawRequest, fields, toJson(spec.jsonSchema()),
                toJson(spec.requiredConstraintLabels()));

        return new Prompt(List.of(
                new SystemMessage("""
                        你是教学管理系统的参数抽取器。只返回一个 JSON 对象，不要 Markdown，不要解释。
                        只抽取用户明确给出的参数；不要编造默认值、ID 或业务数据。
                        字段名必须使用 JSON Schema properties 中的英文名；不要返回 schema 之外的字段。
                        ID、数量、状态等整数字段必须返回数字；未提到的字段不要放入 arguments。
                        """),
                new UserMessage(userPrompt)
        ));
    }

    private Prompt buildRepairPrompt(AgentActionSpec spec,
                                     String rawRequest,
                                     Map<String, Object> invalidArguments,
                                     List<AgentActionValidationIssue> issues) {
        String userPrompt = """
                动作编码：%s
                动作名称：%s
                用户原话：%s

                JSON Schema：
                %s

                上一次 arguments：
                %s

                校验错误：
                %s

                请只修复格式、字段名、类型或枚举问题。用户没有明确提供的业务参数不要补造。
                返回 JSON 格式：
                {"arguments":{"fieldName":"value"},"missingRequiredFields":["fieldName"],"confidence":0.0}
                """.formatted(spec.actionCode(), spec.actionName(), rawRequest, toJson(spec.jsonSchema()),
                toJson(invalidArguments), toJson(validationIssuesToData(issues)));

        return new Prompt(List.of(
                new SystemMessage("""
                        你是教学管理系统的参数 JSON 修复器。只返回一个 JSON 对象，不要 Markdown，不要解释。
                        字段名必须使用 JSON Schema properties 中的英文名；删除 schema 之外的字段。
                        只保留用户明确给出的参数；不要编造默认值、ID 或业务数据。
                        """),
                new UserMessage(userPrompt)
        ));
    }

    private List<Map<String, Object>> validationIssuesToData(List<AgentActionValidationIssue> issues) {
        List<Map<String, Object>> data = new ArrayList<>();
        if (issues == null) {
            return data;
        }
        for (AgentActionValidationIssue issue : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", issue.field());
            item.put("code", issue.code());
            item.put("message", issue.message());
            data.add(item);
        }
        return data;
    }

    private Map<String, Object> argumentsToMap(JsonNode arguments) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        arguments.fields().forEachRemaining(entry -> putIfPresent(parameters, entry.getKey(), jsonValue(entry.getValue())));
        return parameters;
    }

    private Map<String, Object> sanitizeArguments(AgentActionSpec spec, JsonNode arguments) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (AgentActionParameterSpec parameter : spec.parameters()) {
            JsonNode value = firstArgumentValue(arguments, parameter);
            if (value == null || value.isNull()) {
                continue;
            }
            putIfPresent(parameters, parameter.name(), jsonValue(value));
        }
        return parameters;
    }

    private JsonNode firstArgumentValue(JsonNode arguments, AgentActionParameterSpec parameter) {
        for (String acceptedName : parameter.allNamesForExtraction()) {
            JsonNode value = arguments.get(acceptedName);
            if (value != null) {
                return value;
            }
        }
        String snakeName = parameter.name().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        JsonNode value = arguments.get(snakeName);
        if (value != null) {
            return value;
        }
        return null;
    }

    private Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isIntegralNumber()) {
            return value.intValue();
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
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

    private String assistantContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String content = response.getResult().getOutput().getContent();
        return content == null ? "" : content.trim();
    }

    private Map<String, Object> inferWithLocalFallback(AgentActionSpec spec, String rawRequest) {
        Map<String, Object> inferred = new LinkedHashMap<>();
        if (spec == null || isBlank(rawRequest)) {
            return inferred;
        }
        for (AgentActionParameterSpec parameter : spec.parameters()) {
            String name = parameter.name();
            if (isBlank(name)) {
                continue;
            }
            String normalizedName = name.trim().toLowerCase(Locale.ROOT);
            if ("title".equals(normalizedName) || "name".equals(normalizedName)) {
                putIfPresent(inferred, name, inferTitle(spec, rawRequest));
            } else if ("originaltitle".equals(normalizedName) || "oldtitle".equals(normalizedName)) {
                putIfPresent(inferred, name, inferOriginalTitle(rawRequest));
            } else if ("teacher".equals(normalizedName)) {
                putIfPresent(inferred, name, inferText(rawRequest,
                        "(?:授课老师|任课老师|教师|老师|teacher)\\s*(?:是|为|叫|=|:|：)?\\s*([^，,。；;\\n]+)"));
            } else if ("difficulty".equals(normalizedName)) {
                putIfPresent(inferred, name, inferText(rawRequest,
                        "(?:难度|difficulty)\\s*(?:是|为|=|:|：)?\\s*([^，,。；;\\n]+)"));
            } else if ("status".equals(normalizedName)) {
                putIfPresent(inferred, name, inferStatus(rawRequest));
            } else if ("courseid".equals(normalizedName)) {
                putIfPresent(inferred, name, inferCourseId(rawRequest));
            } else if ("coursetitle".equals(normalizedName) || "coursename".equals(normalizedName)) {
                putIfPresent(inferred, name, inferCourseTitle(rawRequest));
            } else if ("categoryid".equals(normalizedName)) {
                putIfPresent(inferred, name, inferInteger(rawRequest,
                        "(?:分类\\s*ID|分类id|分类编号|课程分类ID|课程分类id|课程分类|category\\s*Id|category_id|categoryId)\\s*(?:是|为|=|:|：)?\\s*(\\d+)"));
            } else if (normalizedName.endsWith("id")) {
                String camelLabel = Pattern.quote(name);
                String snakeLabel = Pattern.quote(name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
                putIfPresent(inferred, name, inferInteger(rawRequest,
                        "(?:" + camelLabel + "|" + snakeLabel + ")\\s*(?:是|为|=|:|：)?\\s*(\\d+)"));
            }
        }
        return inferred;
    }

    private String inferTitle(AgentActionSpec spec, String rawRequest) {
        String updatedTitle = inferUpdatedTitle(rawRequest);
        if (!isBlank(updatedTitle) && spec != null && "course.update".equals(spec.actionCode())) {
            return updatedTitle;
        }
        String value = matchText(rawRequest,
                "(?:课程名称|课程标题|课程名|课名|标题|名称|名字)\\s*(?:是|为|叫|设置为|命名为|改成|改为|修改为|更新为|=|:|：)?\\s*([^，,。；;\\n]+)");
        if (!isBlank(value)) {
            return trimCourseSuffix(trimTrailingParameterLabels(value));
        }
        return trimCourseSuffix(matchText(rawRequest,
                "(?:创建|新建|新增|添加|开设|发布|建一门|加一门)\\s*(?:一门|一个|个)?\\s*([^，,。；;\\n]+?)(?:课程|课)"));
    }

    private String inferOriginalTitle(String rawRequest) {
        String value = matchText(rawRequest,
                "(?:原课程名称|原课程标题|原课程名|原课名|原标题|原名称|旧标题|旧名称|当前标题|当前名称)\\s*(?:是|为|叫|=|:|：)?\\s*([^，,。；;\\n]+)");
        if (!isBlank(value)) {
            return normalizeOriginalTitle(value);
        }
        value = matchText(rawRequest,
                "(?:修改|更改|更新|调整|编辑)?\\s*(?:课程名称|课程标题|课程名|课名|标题|名称|名字)\\s*(?:是|为|叫|=|:|：)?\\s*([^，,。；;\\n]+?)\\s*(?:，|,|。|；|;)?\\s*(?:改成|改为|修改为|更新为)");
        if (!isBlank(value)) {
            return normalizeOriginalTitle(value);
        }
        return normalizeOriginalTitle(matchText(rawRequest,
                "(?:把|将)\\s*([^，,。；;\\n]+?)\\s*(?:这门)?(?:课程|课)?\\s*(?:的)?(?:课程名称|课程标题|课程名|课名|标题|名称|名字)\\s*(?:改成|改为|修改为|更新为)"));
    }

    private String inferUpdatedTitle(String rawRequest) {
        String value = matchText(rawRequest,
                "(?:改成|改为|修改为|更新为|命名为|设置为)\\s*([^，,。；;\\n]+)");
        if (!isBlank(value)) {
            return trimCourseSuffix(trimTrailingParameterLabels(value));
        }
        return matchText(rawRequest,
                "(?:新的?课程名称|新的?课程标题|新的?课程名|新的?课名|新标题|新名称)\\s*(?:是|为|叫|=|:|：)?\\s*([^，,。；;\\n]+)");
    }

    private String normalizeOriginalTitle(String value) {
        String title = trimCourseSuffix(trimTrailingParameterLabels(value));
        if (isBlank(title) || title.matches("\\d+\\s*号?")) {
            return null;
        }
        return title;
    }

    private Integer inferCourseId(String rawRequest) {
        Integer explicitId = inferInteger(rawRequest,
                "(?:课程\\s*ID|课程id|课程编号|course\\s*Id|course_id|courseId)\\s*(?:是|为|=|:|：)?\\s*(\\d+)");
        if (explicitId != null) {
            return explicitId;
        }
        return inferInteger(rawRequest, "(\\d+)\\s*号\\s*(?:课程|课)(?!时)");
    }

    private String inferCourseTitle(String rawRequest) {
        String value = matchText(rawRequest,
                "(?:给|为|在|针对)\\s*([^，,。；;\\n]+?)\\s*(?:这门|该门)?(?:课程|课)\\s*(?:创建|新建|新增|添加|安排).*?(?:章节|课时|教学班|试卷|题目)");
        if (!isBlank(value)) {
            return normalizeCourseTitle(value);
        }
        value = matchText(rawRequest,
                "(?:给|为|在|针对)\\s*([^，,。；;\\n]+?)\\s*(?:布置|发布|创建|新建|安排|留一份|留个).*?(?:作业|练习|任务)");
        if (!isBlank(value)) {
            return normalizeCourseTitle(value);
        }
        value = matchText(rawRequest,
                "(?:课程名称|课程标题|课程名|课名)\\s*(?:是|为|叫|=|:|：)?\\s*([^，,。；;\\n]+)");
        if (!isBlank(value)) {
            return normalizeCourseTitle(value);
        }
        value = matchText(rawRequest,
                "(?:叫|名叫|名字叫|名称叫)\\s*([^，,。；;？?\\n]+?)(?:的)?(?:课程|课|门课)(?:吗|么|呢)?\\s*$");
        return normalizeCourseTitle(value);
    }

    private String normalizeCourseTitle(String value) {
        if (value == null) {
            return null;
        }
        return trimCourseSuffix(trimTrailingParameterLabels(value))
                .replaceFirst("^(?:一门|一个|个|这门|该门|这|这个)\\s*", "")
                .trim();
    }

    private Integer inferStatus(String rawRequest) {
        if (isBlank(rawRequest)) {
            return null;
        }
        if (containsAny(rawRequest, "拒绝", "驳回", "不通过", "未通过")) {
            return 2;
        }
        if (containsAny(rawRequest, "审核通过", "通过", "同意", "批准")) {
            return 1;
        }
        if (containsAny(rawRequest, "上架", "启用", "恢复", "正常", "发布")) {
            return 1;
        }
        if (containsAny(rawRequest, "草稿")) {
            return 2;
        }
        if (containsAny(rawRequest, "下架", "停用", "关闭", "删除", "移除", "撤销", "作废", "删掉", "删去", "归档")) {
            return 0;
        }
        return null;
    }

    private String inferText(String rawRequest, String regex) {
        return trimTrailingParameterLabels(matchText(rawRequest, regex));
    }

    private Integer inferInteger(String rawRequest, String regex) {
        String text = matchText(rawRequest, regex);
        if (isBlank(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String matchText(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String trimTrailingParameterLabels(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.replaceFirst("\\s*(?:授课老师|任课老师|教师|难度|分类\\s*ID|分类id|分类编号|课程分类|category\\s*Id|category_id|categoryId).*$", "").trim();
    }

    private String trimCourseSuffix(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceFirst("(?:的)?(?:课程|课)$", "").trim();
    }

    private boolean containsAny(String value, String... candidates) {
        if (value == null || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String parameterType(AgentActionParameterSpec parameter) {
        return parameter.type().jsonSchemaType();
    }

    private void putIfPresent(Map<String, Object> parameters, String key, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            parameters.put(key, text.trim());
        } else if (value != null) {
            parameters.put(key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
