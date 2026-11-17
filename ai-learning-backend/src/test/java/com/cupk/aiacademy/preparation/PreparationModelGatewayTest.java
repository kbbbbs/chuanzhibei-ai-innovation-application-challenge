package com.cupk.aiacademy.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.core.llm.ChatModelTimeoutException;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.model.ModelInvocationException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class PreparationModelGatewayTest {
    @Test
    void parsesFencedJsonWithTrailingExplanationAndLenientSyntax() {
        PreparationModelGateway gateway = gateway(mock(ChatModelClient.class), 1);
        String raw = """
                下面是结果：
                ```json
                {'courseDescription':'人工智能课程','chapters':[],}
                ```
                以上内容可直接使用。[说明]
                """;

        PreparationModels.Outline outline = gateway.parse(raw, PreparationModels.Outline.class);

        assertEquals("人工智能课程", outline.getCourseDescription());
        assertTrue(outline.getChapters().isEmpty());
        assertEquals("{'courseDescription':'人工智能课程','chapters':[],}", gateway.cleanJson(raw));
    }

    @Test
    void retriesOnceAfterModelTimeout() throws Exception {
        ChatModelClient chatModel = mock(ChatModelClient.class);
        AtomicInteger calls = new AtomicInteger();
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("重试成功"))));
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) throw new ChatModelTimeoutException("controlled timeout", null);
            return response;
        });
        PreparationModelGateway gateway = gateway(chatModel, 2);

        String result = gateway.generateMarkdown("system", "user");

        assertEquals("重试成功", result);
        assertEquals(2, calls.get());
    }

    @Test
    void normalizesStringOptionsAndAnswersBeforeDtoDeserialization() {
        PreparationModelGateway gateway = gateway(mock(ChatModelClient.class), 1);
        String raw = """
                [{
                  "content":"Transformer使用自注意力机制。",
                  "type":"true-false",
                  "difficulty":"medium",
                  "knowledgePoint":"Transformer",
                  "analysis":"自注意力是Transformer的核心。",
                  "options":["A. 正确","B. 错误"],
                  "answers":["正确"]
                }]
                """;

        List<QuestionRequestDTO> questions = gateway.parseQuestions(raw);

        QuestionRequestDTO question = questions.get(0);
        assertEquals("TRUE_FALSE", question.getType().name());
        assertEquals("A", question.getOptions().get(0).getOptionKey());
        assertEquals("正确", question.getOptions().get(0).getOptionContent());
        assertEquals(1, question.getOptions().get(0).getSortOrder());
        assertEquals("A", question.getAnswers().get(0).getAnswerContent());
        assertEquals(1, question.getAnswers().get(0).getSortOrder());
    }

    @Test
    void normalizesOptionAndAnswerAliasObjects() {
        PreparationModelGateway gateway = gateway(mock(ChatModelClient.class), 1);
        String raw = """
                [{
                  "content":"选择正确选项",
                  "type":"single_choice",
                  "difficulty":"EASY",
                  "options":[{"key":"A","content":"选项一"},{"key":"B","content":"选项二"}],
                  "answers":{"answer":"B"}
                }]
                """;

        QuestionRequestDTO question = gateway.parseQuestions(raw).get(0);

        assertEquals("选项二", question.getOptions().get(1).getOptionContent());
        assertEquals(2, question.getOptions().get(1).getSortOrder());
        assertEquals("B", question.getAnswers().get(0).getAnswerContent());
    }

    @Test
    void acceptsChineseEnumsUnknownFieldsAndMissingTrueFalseOptions() {
        PreparationModelGateway gateway = gateway(mock(ChatModelClient.class), 1);
        String raw = """
                [{
                  "content":"图灵测试关注机器的外在行为。",
                  "type":"判断题",
                  "difficulty":"简单",
                  "analysis":"供教师审核",
                  "modelComment":"这个多余字段应被忽略",
                  "answers":"正确"
                }]
                """;

        QuestionRequestDTO question = gateway.parseQuestions(raw).get(0);

        assertEquals("TRUE_FALSE", question.getType().name());
        assertEquals("EASY", question.getDifficulty().name());
        assertEquals(2, question.getOptions().size());
        assertEquals("A", question.getAnswers().get(0).getAnswerContent());
    }

    @Test
    void coercesArraysReturnedForStringFields() {
        PreparationModelGateway gateway = gateway(mock(ChatModelClient.class), 1);
        String raw = """
                [{
                  "content":["图灵测试", "关注机器的外在行为"],
                  "type":"SHORT_ANSWER",
                  "difficulty":"MEDIUM",
                  "knowledgePoint":["人工智能", "图灵测试"],
                  "analysis":["行为主义视角", "需要人工审核"],
                  "answers":[{"answerContent":["机器是否表现得像人"],"sortOrder":1}]
                }]
                """;

        QuestionRequestDTO question = gateway.parseQuestions(raw).get(0);

        assertEquals("图灵测试、关注机器的外在行为", question.getContent());
        assertEquals("人工智能、图灵测试", question.getKnowledgePoint());
        assertEquals("机器是否表现得像人", question.getAnswers().get(0).getAnswerContent());
    }

    @Test
    void coercesOutlineStringFieldsAndFlattensKnowledgePoints() {
        ChatModelClient chatModel = mock(ChatModelClient.class);
        String raw = """
                {
                  "courseDescription":["人工智能", "基础课程"],
                  "chapters":[{
                    "chapterKey":"c1",
                    "title":["第一章", "人工智能导论"],
                    "description":["发展历史"],
                    "sortOrder":[1],
                    "lessons":[{
                      "lessonKey":"l1",
                      "title":"图灵测试",
                      "objectives":["理解图灵测试", "辨析中文屋"],
                      "knowledgePoints":[["图灵测试"],["中文屋"]],
                      "durationMinutes":[45],
                      "sortOrder":[1]
                    }]
                  }]
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(raw)))));
        PreparationModelGateway gateway = gateway(chatModel, 1);

        PreparationModels.Outline outline = gateway.generateOutline("system", "user");

        assertEquals("人工智能、基础课程", outline.getCourseDescription());
        assertEquals("第一章、人工智能导论", outline.getChapters().get(0).getTitle());
        assertEquals("理解图灵测试、辨析中文屋", outline.getChapters().get(0).getLessons().get(0).getObjectives());
        assertEquals(List.of("图灵测试", "中文屋"), outline.getChapters().get(0).getLessons().get(0).getKnowledgePoints());
    }

    @Test
    void usesTheSharedGenerationClientsConfiguredBudget() {
        ChatModelClient chatModel = mock(ChatModelClient.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("课时内容")))));

        assertEquals("课时内容", gateway(chatModel, 1).generateMarkdown("system", "user"));

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void exhaustedTimeoutKeepsAnActionableSafeReason() {
        ChatModelClient chatModel = mock(ChatModelClient.class);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ChatModelTimeoutException("PRIVATE_PROVIDER_RESPONSE", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> gateway(chatModel, 2).generateMarkdown("system", "user"));

        assertTrue(error.getMessage().contains("超时"));
        assertTrue(error.getMessage().contains("2次"));
        assertFalse(error.getMessage().contains("PRIVATE_PROVIDER_RESPONSE"));
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void authenticationFailureIsReportedWithoutRetryingOrLeakingProviderText() {
        ChatModelClient chatModel = mock(ChatModelClient.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new ModelInvocationException("MODEL_HTTP_401"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> gateway(chatModel, 2).generateMarkdown("system", "user"));

        assertTrue(error.getMessage().contains("认证失败"));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void malformedOutlineAfterOneRepairKeepsASafeFormatReason() {
        ChatModelClient chatModel = mock(ChatModelClient.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("PRIVATE_MODEL_CONTENT")))));

        BusinessException error = assertThrows(BusinessException.class,
                () -> gateway(chatModel, 1).generateOutline("system", "user"));

        assertTrue(error.getMessage().contains("骨架格式不正确"));
        assertFalse(error.getMessage().contains("PRIVATE_MODEL_CONTENT"));
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    private PreparationModelGateway gateway(ChatModelClient chatModel, int maxAttempts) {
        return new PreparationModelGateway(chatModel, new ObjectMapper(), maxAttempts);
    }
}
