package com.cupk.aiacademy.preparation;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class PreparationModelGateway {
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ExecutorService modelExecutor;
    private final long timeoutSeconds;

    public PreparationModelGateway(OpenAiChatModel chatModel,
                                   ObjectMapper objectMapper,
                                   @Qualifier("preparationModelExecutor") ExecutorService modelExecutor,
                                   @Value("${preparation.model-timeout-seconds:90}") long timeoutSeconds) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.modelExecutor = modelExecutor;
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
    }

    public PreparationModels.Outline generateOutline(String systemPrompt, String userPrompt) {
        String raw = call(systemPrompt, userPrompt);
        try {
            return parse(raw, PreparationModels.Outline.class);
        } catch (RuntimeException first) {
            return parse(call("你是JSON格式修复器。只输出修复后的合法JSON，不添加解释。",
                    "目标结构为{courseDescription:string,chapters:[{chapterKey,title,description,sortOrder,lessons:[{lessonKey,title,objectives,knowledgePoints:string[],durationMinutes,sortOrder}]}]}。\n待修复内容：\n" + raw),
                    PreparationModels.Outline.class);
        }
    }

    public List<QuestionRequestDTO> generateQuestions(String systemPrompt, String userPrompt) {
        String raw = call(systemPrompt, userPrompt);
        try {
            return parse(raw, new TypeReference<>() {});
        } catch (RuntimeException first) {
            return parse(call("你是JSON格式修复器。只输出修复后的合法JSON数组，不添加解释。",
                    "将下面内容修复为QuestionRequestDTO数组；字段包括content,type,difficulty,knowledgePoint,analysis,options,answers。\n" + raw),
                    new TypeReference<>() {});
        }
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
        Future<ChatResponse> future = modelExecutor.submit(() -> chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt), new UserMessage(userPrompt)))));
        try {
            ChatResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            String text = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) throw new BusinessException("模型返回为空");
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            future.cancel(true);
            throw new BusinessException("智能备课模型调用失败: " + rootMessage(e));
        }
    }

    private <T> T parse(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(cleanJson(raw), type);
        } catch (JsonProcessingException e) {
            throw new BusinessException("模型JSON格式不正确");
        }
    }

    private <T> T parse(String raw, TypeReference<T> type) {
        try {
            return objectMapper.readValue(cleanJson(raw), type);
        } catch (JsonProcessingException e) {
            throw new BusinessException("模型JSON格式不正确");
        }
    }

    private String cleanJson(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            if (newline >= 0) text = text.substring(newline + 1);
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        }
        int object = text.indexOf('{');
        int array = text.indexOf('[');
        int start = object < 0 ? array : array < 0 ? object : Math.min(object, array);
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        return start >= 0 && end >= start ? text.substring(start, end + 1) : text;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
