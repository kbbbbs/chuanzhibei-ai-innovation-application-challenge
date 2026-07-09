package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentParameterExtractorTest {
    @Test
    void extractUsesModelJsonForCourseCreate() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse("""
                ```json
                {"arguments":{"title":"我不想吃肉","categoryId":1},"missingRequiredFields":[],"confidence":0.96}
                ```
                """));
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper(), chatModelClient);

        AgentParameterExtractionResult result = extractor.extract(
                courseCreateSpec(),
                "帮我创建一门课程，标题是我不想吃肉，分类ID是1"
        );

        assertEquals("我不想吃肉", result.parameters().get("title"));
        assertEquals(1, result.parameters().get("categoryId"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertTrue(result.modelAttempted());
        assertTrue(result.modelSucceeded());
        assertEquals("model", result.source());
        verify(chatModelClient).call(any(Prompt.class));
    }

    @Test
    void extractFallsBackWhenModelReturnsInvalidJson() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse("我会帮你创建课程"));
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper(), chatModelClient);

        AgentParameterExtractionResult result = extractor.extract(
                courseCreateSpec(),
                "帮我创建一门课程，标题是我不想吃肉，分类ID是1"
        );

        assertEquals("我不想吃肉", result.parameters().get("title"));
        assertEquals(1, result.parameters().get("categoryId"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertTrue(result.modelAttempted());
        assertFalse(result.modelSucceeded());
        assertEquals("fallback", result.source());
    }

    @Test
    void extractReportsMissingRequiredFieldsAfterPartialExtraction() {
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper());

        AgentParameterExtractionResult result = extractor.extract(
                courseCreateSpec(),
                "帮我创建一门 Java 入门课程"
        );

        assertEquals("Java 入门", result.parameters().get("title"));
        assertEquals(List.of("categoryId"), result.missingRequiredFields());
        assertFalse(result.modelAttempted());
        assertFalse(result.modelSucceeded());
        assertEquals("fallback", result.source());
    }

    @Test
    void extractInfersCourseCreateWithCompactCategoryLabel() {
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper());

        AgentParameterExtractionResult result = extractor.extract(
                courseCreateSpec(),
                "新建一门课程，名字叫我只睡了5个多小时，课程分类1"
        );

        assertEquals("我只睡了5个多小时", result.parameters().get("title"));
        assertEquals(1, result.parameters().get("categoryId"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertEquals("fallback", result.source());
    }

    @Test
    void extractValidatesModelOutputAgainstSchema() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"arguments":{"title":"Schema 测试","categoryId":"不是数字"},"missingRequiredFields":[],"confidence":0.9}
                """));
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper(), chatModelClient);

        AgentParameterExtractionResult result = extractor.extract(
                courseCreateSpec(),
                "帮我创建一门课程，标题是 Schema 测试"
        );

        assertEquals("Schema 测试", result.parameters().get("title"));
        assertEquals(List.of("categoryId"), result.missingRequiredFields());
        assertEquals(List.of("type_mismatch"),
                result.validationIssues().stream().map(AgentActionValidationIssue::code).toList());
        assertTrue(result.modelAttempted());
        assertTrue(result.modelSucceeded());
        assertEquals("model", result.source());
    }

    @Test
    void extractInfersCourseStatusDeleteByNaturalLanguageFilters() {
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper());

        AgentParameterExtractionResult result = extractor.extract(
                courseStatusSpec(),
                "删除标题是我有点想吃肉的课，授课老师王老师，难度入门，分类ID是1"
        );

        assertEquals("我有点想吃肉", result.parameters().get("title"));
        assertEquals("王老师", result.parameters().get("teacher"));
        assertEquals("入门", result.parameters().get("difficulty"));
        assertEquals(1, result.parameters().get("categoryId"));
        assertEquals(0, result.parameters().get("status"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertEquals("fallback", result.source());
    }

    @Test
    void extractInfersCourseStatusByCourseNameAlias() {
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper());

        AgentParameterExtractionResult result = extractor.extract(
                courseStatusSpec(),
                "下架课程名为高勋的课"
        );

        assertEquals("高勋", result.parameters().get("title"));
        assertEquals(0, result.parameters().get("status"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertEquals("fallback", result.source());
    }

    @Test
    void extractInfersCourseUpdateOriginalAndNewTitle() {
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper());

        AgentParameterExtractionResult result = extractor.extract(
                courseUpdateSpec(),
                "修改标题是我不想吃肉，改成我想吃肉"
        );

        assertEquals("我不想吃肉", result.parameters().get("originalTitle"));
        assertEquals("我想吃肉", result.parameters().get("title"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertEquals("fallback", result.source());
    }

    @Test
    void extractUsesModelJsonForChapterCreateCourseTitle() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"arguments":{"courseTitle":"Java 入门","title":"人工输入测试章节"},"missingRequiredFields":[],"confidence":0.95}
                """));
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper(), chatModelClient);

        AgentParameterExtractionResult result = extractor.extract(
                chapterCreateSpec(),
                "帮我给 Java 入门 这门课创建一个章节，标题叫人工输入测试章节"
        );

        assertEquals("Java 入门", result.parameters().get("courseTitle"));
        assertEquals("人工输入测试章节", result.parameters().get("title"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertTrue(result.modelAttempted());
        assertTrue(result.modelSucceeded());
        assertEquals("model", result.source());
    }

    @Test
    void extractRepairsInvalidModelOutputOnce() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class)))
                .thenReturn(chatResponse("""
                        {"arguments":{"title":"Schema 修复","categoryId":"不是数字","extra":"x"},"missingRequiredFields":[],"confidence":0.9}
                        """))
                .thenReturn(chatResponse("""
                        {"arguments":{"title":"Schema 修复","categoryId":2},"missingRequiredFields":[],"confidence":0.9}
                        """));
        AgentParameterExtractor extractor = new AgentParameterExtractor(new ObjectMapper(), chatModelClient);

        AgentParameterExtractionResult result = extractor.extract(
                courseCreateSpec(),
                "帮我创建一门课程，标题是 Schema 修复，分类ID是2"
        );

        assertEquals("Schema 修复", result.parameters().get("title"));
        assertEquals(2, result.parameters().get("categoryId"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertTrue(result.validationIssues().isEmpty());
        assertEquals("model_repair", result.source());
        verify(chatModelClient, times(2)).call(any(Prompt.class));
    }

    private AgentActionSpec courseCreateSpec() {
        return new AgentActionSpec(
                "course.create",
                "创建课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备创建课程",
                List.of(
                        new AgentActionParameterSpec("title", "课程名称", true),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", true)
                ),
                List.of("会新增课程"),
                Duration.ofMinutes(10)
        );
    }

    private AgentActionSpec courseStatusSpec() {
        return new AgentActionSpec(
                "course.status",
                "变更课程状态",
                "course",
                AgentActionRiskLevel.HIGH,
                true,
                "确认变更课程状态",
                "准备变更课程状态",
                List.of(
                        new AgentActionParameterSpec("courseId", "课程ID", false),
                        new AgentActionParameterSpec("status", "目标状态", true),
                        new AgentActionParameterSpec("title", "课程名称", false),
                        new AgentActionParameterSpec("teacher", "教师展示名", false),
                        new AgentActionParameterSpec("difficulty", "课程难度", false),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", false)
                ),
                List.of("会变更课程状态"),
                Duration.ofMinutes(10)
        );
    }

    private AgentActionSpec courseUpdateSpec() {
        return new AgentActionSpec(
                "course.update",
                "修改课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备修改课程",
                List.of(
                        new AgentActionParameterSpec("courseId", "课程ID", false),
                        new AgentActionParameterSpec("originalTitle", "原课程名称", false),
                        new AgentActionParameterSpec("title", "新的课程名称", false),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", false)
                ),
                List.of("会修改课程"),
                Duration.ofMinutes(10)
        );
    }

    private AgentActionSpec chapterCreateSpec() {
        return new AgentActionSpec(
                "chapter.create",
                "创建章节",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备创建章节",
                List.of(
                        new AgentActionParameterSpec("courseId", "课程ID", false),
                        new AgentActionParameterSpec("courseTitle", "课程名称", false),
                        new AgentActionParameterSpec("title", "章节标题", true)
                ),
                List.of("会新增章节"),
                Duration.ofMinutes(10)
        );
    }

    private ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
