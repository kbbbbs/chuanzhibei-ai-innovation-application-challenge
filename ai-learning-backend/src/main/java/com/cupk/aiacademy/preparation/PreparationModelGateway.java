package com.cupk.aiacademy.preparation;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PreparationModelGateway {
    private static final Logger log = LoggerFactory.getLogger(PreparationModelGateway.class);
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ObjectMapper tolerantObjectMapper;
    private final ExecutorService modelExecutor;
    private final long timeoutSeconds;
    private final int maxAttempts;

    public PreparationModelGateway(OpenAiChatModel chatModel,
                                   ObjectMapper objectMapper,
                                   @Qualifier("preparationModelExecutor") ExecutorService modelExecutor,
                                   @Value("${preparation.model-timeout-seconds:90}") long timeoutSeconds,
                                   @Value("${preparation.model-max-attempts:2}") int maxAttempts) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.tolerantObjectMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.modelExecutor = modelExecutor;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxAttempts = Math.max(1, Math.min(3, maxAttempts));
    }

    public PreparationModels.Outline generateOutline(String systemPrompt, String userPrompt) {
        String raw = call(systemPrompt, userPrompt);
        try {
            return parseOutline(raw);
        } catch (RuntimeException first) {
            return parseOutline(call("你是JSON格式修复器。只输出修复后的合法JSON，不添加解释。",
                    "目标结构为{courseDescription:string,chapters:[{chapterKey,title,description,sortOrder,lessons:[{lessonKey,title,objectives,knowledgePoints:string[],durationMinutes,sortOrder}]}]}。\n待修复内容：\n" + raw));
        }
    }

    private PreparationModels.Outline parseOutline(String raw) {
        String json = cleanJson(raw);
        try {
            JsonNode root = tolerantObjectMapper.readTree(json);
            if (root != null && root.isObject()) {
                JsonNode nested = firstPresent(root, "outline", "data", "result");
                if (nested != null && nested.isObject()) root = nested;
            }
            if (!(root instanceof ObjectNode outline)) throw new BusinessException("模型课程骨架JSON必须是对象");
            coerceTextField(outline, "courseDescription");
            JsonNode chapters = outline.get("chapters");
            if (chapters != null && chapters.isArray()) {
                for (JsonNode chapterNode : chapters) {
                    if (!(chapterNode instanceof ObjectNode chapter)) continue;
                    for (String field : List.of("chapterKey", "title", "description")) coerceTextField(chapter, field);
                    coerceIntField(chapter, "sortOrder", 1);
                    JsonNode lessons = chapter.get("lessons");
                    if (lessons != null && lessons.isArray()) {
                        for (JsonNode lessonNode : lessons) {
                            if (!(lessonNode instanceof ObjectNode lesson)) continue;
                            for (String field : List.of("lessonKey", "title", "objectives")) coerceTextField(lesson, field);
                            normalizeStringArray(lesson, "knowledgePoints");
                            coerceIntField(lesson, "durationMinutes", 45);
                            coerceIntField(lesson, "sortOrder", 1);
                        }
                    }
                }
            }
            return tolerantObjectMapper.treeToValue(outline, PreparationModels.Outline.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Preparation outline JSON normalization failed: {}; preview={}", rootMessage(e), preview(json));
            throw new BusinessException("模型课程骨架JSON格式不正确: " + rootMessage(e));
        }
    }

    public List<QuestionRequestDTO> generateQuestions(String systemPrompt, String userPrompt) {
        String raw = call(systemPrompt, userPrompt);
        try {
            return parseQuestions(raw);
        } catch (RuntimeException first) {
            return parseQuestions(call("你是JSON格式修复器。只输出修复后的合法JSON数组，不添加解释。",
                    "将下面内容修复为QuestionRequestDTO数组。options必须为[{optionKey,optionContent,sortOrder}]；"
                            + "answers必须为[{answerContent,sortOrder}]。\n" + raw));
        }
    }

    public List<QuestionRequestDTO> parseQuestions(String raw) {
        String json = cleanJson(raw);
        try {
            JsonNode root = tolerantObjectMapper.readTree(json);
            if (root != null && root.isObject()) {
                JsonNode nested = firstPresent(root, "questions", "items", "data", "results");
                if (nested != null) root = nested;
            }
            if (root == null || !root.isArray()) {
                throw new BusinessException("模型题目JSON必须是数组");
            }
            ArrayNode questions = (ArrayNode) root;
            for (JsonNode node : questions) {
                if (node instanceof ObjectNode question) normalizeQuestionNode(question);
            }
            return tolerantObjectMapper.readValue(objectMapper.writeValueAsString(questions), new TypeReference<>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Preparation question JSON normalization failed: {}; preview={}", rootMessage(e), preview(json));
            throw new BusinessException("模型题目JSON格式不正确: " + rootMessage(e));
        }
    }

    private void normalizeQuestionNode(ObjectNode question) {
        for (String field : List.of("content", "knowledgePoint", "analysis")) coerceTextField(question, field);
        normalizeQuestionType(question);
        normalizeDifficulty(question);
        ArrayNode options = normalizeOptions(question.get("options"));
        if (options == null && "TRUE_FALSE".equals(question.path("type").asText())) {
            options = objectMapper.createArrayNode();
            options.add(optionNode("A", "正确", 1));
            options.add(optionNode("B", "错误", 2));
        }
        if (options != null) question.set("options", options);
        ArrayNode answers = normalizeAnswers(question.get("answers"), options);
        if (answers != null) question.set("answers", answers);
    }

    private ArrayNode normalizeOptions(JsonNode source) {
        if (source == null || source.isNull()) return null;
        ArrayNode normalized = objectMapper.createArrayNode();
        if (source.isObject()) {
            source.fields().forEachRemaining(entry -> normalized.add(optionNode(entry.getKey(), entry.getValue().asText(), normalized.size() + 1)));
            return normalized;
        }
        if (!source.isArray()) return normalized;
        for (JsonNode option : source) {
            int order = normalized.size() + 1;
            if (option.isTextual()) {
                String raw = option.asText().trim();
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("^([A-Za-z0-9]{1,8})\\s*[.、:：)）-]\\s*(.+)$")
                        .matcher(raw);
                String key = matcher.matches() ? matcher.group(1) : optionKey(order);
                String content = matcher.matches() ? matcher.group(2).trim() : raw;
                normalized.add(optionNode(key, content, order));
            } else if (option.isObject()) {
                ObjectNode value = (ObjectNode) option.deepCopy();
                String key = text(value, "optionKey", "key", "label", "code");
                String content = text(value, "optionContent", "content", "text", "value");
                value.put("optionKey", key == null ? optionKey(order) : key);
                value.put("optionContent", content == null ? "选项" + optionKey(order) : content);
                value.put("sortOrder", positiveInt(value, order, "sortOrder", "order", "index"));
                removeFields(value, "key", "label", "code", "content", "text", "value", "order", "index");
                normalized.add(value);
            }
        }
        return normalized;
    }

    private ArrayNode normalizeAnswers(JsonNode source, ArrayNode options) {
        if (source == null || source.isNull()) return null;
        List<JsonNode> values = new ArrayList<>();
        if (source.isArray()) source.forEach(values::add);
        else values.add(source);
        ArrayNode normalized = objectMapper.createArrayNode();
        for (JsonNode answer : values) {
            if (answer.isTextual()) {
                for (String part : answer.asText().split("[,，;；]+")) {
                    String content = normalizeAnswerContent(part, options);
                    if (!content.isBlank()) normalized.add(answerNode(content, normalized.size() + 1));
                }
            } else if (answer.isObject()) {
                ObjectNode value = (ObjectNode) answer.deepCopy();
                String content = text(value, "answerContent", "answer", "content", "text", "value");
                value.put("answerContent", normalizeAnswerContent(content == null ? "" : content, options));
                value.put("sortOrder", positiveInt(value, normalized.size() + 1, "sortOrder", "order", "index"));
                removeFields(value, "answer", "content", "text", "value", "order", "index");
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeAnswerContent(String raw, ArrayNode options) {
        String answer = raw == null ? "" : raw.trim().replaceFirst("^(答案|正确答案)\\s*[:：]\\s*", "");
        java.util.regex.Matcher prefixed = java.util.regex.Pattern
                .compile("^([A-Za-z0-9]{1,8})\\s*[.、:：)）-].*$")
                .matcher(answer);
        if (prefixed.matches()) return prefixed.group(1);
        if (options != null) {
            for (JsonNode option : options) {
                if (answer.equalsIgnoreCase(option.path("optionKey").asText())
                        || answer.equalsIgnoreCase(option.path("optionContent").asText())) {
                    return option.path("optionKey").asText();
                }
            }
        }
        return answer;
    }

    private ObjectNode optionNode(String key, String content, int order) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("optionKey", key);
        option.put("optionContent", content);
        option.put("sortOrder", order);
        return option;
    }

    private ObjectNode answerNode(String content, int order) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("answerContent", content);
        answer.put("sortOrder", order);
        return answer;
    }

    private String optionKey(int order) {
        return order <= 26 ? String.valueOf((char) ('A' + order - 1)) : String.valueOf(order);
    }

    private void normalizeQuestionType(ObjectNode question) {
        String raw = nodeText(question.get("type")).trim().toUpperCase(Locale.ROOT).replace('-', '_');
        String normalized = switch (raw) {
            case "单选", "单选题", "SINGLE", "SINGLECHOICE" -> "SINGLE_CHOICE";
            case "多选", "多选题", "MULTIPLE", "MULTIPLECHOICE" -> "MULTIPLE_CHOICE";
            case "判断", "判断题", "TRUEFALSE", "BOOLEAN" -> "TRUE_FALSE";
            case "填空", "填空题", "FILL", "FILLBLANK" -> "FILL_BLANK";
            case "简答", "简答题", "SHORTANSWER", "SUBJECTIVE" -> "SHORT_ANSWER";
            default -> Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER")
                    .contains(raw) ? raw : "SHORT_ANSWER";
        };
        question.put("type", normalized);
    }

    private void normalizeDifficulty(ObjectNode question) {
        String raw = nodeText(question.get("difficulty")).trim().toUpperCase(Locale.ROOT);
        String normalized = switch (raw) {
            case "简单", "初级", "BEGINNER", "LOW" -> "EASY";
            case "困难", "高级", "ADVANCED", "HIGH" -> "HARD";
            case "EASY", "MEDIUM", "HARD" -> raw;
            default -> "MEDIUM";
        };
        question.put("difficulty", normalized);
    }

    private JsonNode firstPresent(JsonNode node, String... fields) {
        for (String field : fields) if (node.has(field)) return node.get(field);
        return null;
    }

    private String text(ObjectNode node, String... fields) {
        JsonNode value = firstPresent(node, fields);
        return value == null || value.isNull() ? null : nodeText(value);
    }

    private int positiveInt(ObjectNode node, int fallback, String... fields) {
        JsonNode value = firstPresent(node, fields);
        int parsed = value == null ? fallback : value.asInt(fallback);
        return parsed > 0 ? parsed : fallback;
    }

    private void removeFields(ObjectNode node, String... fields) {
        for (String field : fields) node.remove(field);
    }

    private void coerceTextField(ObjectNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) node.put(field, nodeText(node.get(field)));
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isValueNode()) return node.asText();
        List<String> values = new ArrayList<>();
        collectScalarTexts(node, values);
        return String.join("、", values);
    }

    private void collectScalarTexts(JsonNode node, List<String> values) {
        if (node == null || node.isNull()) return;
        if (node.isValueNode()) {
            String value = node.asText().trim();
            if (!value.isEmpty()) values.add(value);
            return;
        }
        node.forEach(child -> collectScalarTexts(child, values));
    }

    private void normalizeStringArray(ObjectNode node, String field) {
        JsonNode source = node.get(field);
        ArrayNode normalized = objectMapper.createArrayNode();
        if (source != null && !source.isNull()) {
            List<String> values = new ArrayList<>();
            collectScalarTexts(source, values);
            values.forEach(normalized::add);
        }
        node.set(field, normalized);
    }

    private void coerceIntField(ObjectNode node, String field, int fallback) {
        JsonNode source = node.get(field);
        if (source == null || source.isNull()) return;
        String value = nodeText(source).split("、", 2)[0];
        try { node.put(field, Integer.parseInt(value)); }
        catch (Exception ignored) { node.put(field, fallback); }
    }

    public String generateMarkdown(String systemPrompt, String userPrompt) {
        String text = call(systemPrompt, userPrompt).trim();
        if (text.startsWith("```markdown")) text = text.substring(11);
        else if (text.startsWith("```md")) text = text.substring(5);
        else if (text.startsWith("```")) text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        text = text.trim();
        if (text.isEmpty()) throw new BusinessException("模型未返回课时内容");
        return text;
    }

    private String call(String systemPrompt, String userPrompt) {
        String lastError = "未知错误";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Future<ChatResponse> future = modelExecutor.submit(() -> chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt), new UserMessage(userPrompt)))));
            try {
                ChatResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
                String text = response == null || response.getResult() == null
                        ? null : response.getResult().getOutput().getText();
                if (text == null || text.isBlank()) throw new BusinessException("模型返回为空");
                return text;
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new BusinessException("智能备课模型调用被中断");
            } catch (TimeoutException e) {
                future.cancel(true);
                lastError = "单次调用超过" + timeoutSeconds + "秒";
            } catch (ExecutionException e) {
                future.cancel(true);
                lastError = rootMessage(e);
            } catch (BusinessException e) {
                future.cancel(true);
                lastError = e.getMessage();
            }
            if (attempt < maxAttempts) {
                log.warn("Preparation model call failed on attempt {}/{}, retrying: {}", attempt, maxAttempts, lastError);
            }
        }
        throw new BusinessException("智能备课模型调用失败，已重试" + maxAttempts + "次: " + lastError);
    }

    <T> T parse(String raw, Class<T> type) {
        String json = cleanJson(raw);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException strictError) {
            try {
                return tolerantObjectMapper.readValue(json, type);
            } catch (JsonProcessingException tolerantError) {
                log.warn("Preparation model JSON parse failed: {}; preview={}",
                        tolerantError.getClass().getSimpleName(), preview(json));
                throw new BusinessException("模型JSON格式不正确，请重试或调整生成要求");
            }
        }
    }

    <T> T parse(String raw, TypeReference<T> type) {
        String json = cleanJson(raw);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException strictError) {
            try {
                return tolerantObjectMapper.readValue(json, type);
            } catch (JsonProcessingException tolerantError) {
                log.warn("Preparation model JSON parse failed: {}; preview={}",
                        tolerantError.getClass().getSimpleName(), preview(json));
                throw new BusinessException("模型JSON格式不正确，请重试或调整生成要求");
            }
        }
    }

    String cleanJson(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            if (newline >= 0) text = text.substring(newline + 1);
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        }
        int object = text.indexOf('{');
        int array = text.indexOf('[');
        int start = object < 0 ? array : array < 0 ? object : Math.min(object, array);
        if (start < 0) return text;
        String balanced = firstBalancedJson(text, start);
        return balanced == null ? text.substring(start) : balanced;
    }

    private String firstBalancedJson(String text, int start) {
        Deque<Character> expectedClosings = new ArrayDeque<>();
        boolean escaped = false;
        char quote = 0;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) quote = 0;
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
            } else if (current == '{') {
                expectedClosings.push('}');
            } else if (current == '[') {
                expectedClosings.push(']');
            } else if (current == '}' || current == ']') {
                if (expectedClosings.isEmpty() || expectedClosings.pop() != current) return null;
                if (expectedClosings.isEmpty()) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    private String preview(String value) {
        return "[private model content omitted; characters=" + (value == null ? 0 : value.length()) + "]";
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getClass().getSimpleName();
    }
}
