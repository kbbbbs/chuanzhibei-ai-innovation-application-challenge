package com.cupk.aiacademy.assistant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AssistantAnswerStreamTest {
    @Test
    void incompleteEscapesKeepOnlyDecodedVisibleText() {
        String opening = "{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"";
        assertEquals(
                "行一\n公式 \\(x\\) \"引号\"",
                AssistantAnswerStream.sections(
                                opening + "行一\\n公式 \\\\(x\\\\) \\\"引号\\\"\\u4", false, false)
                        .getFirst()
                        .get("content"));
        assertEquals(
                "字中",
                AssistantAnswerStream.sections(opening + "字\\u4e2d", false, false)
                        .getFirst()
                        .get("content"));
        assertEquals(
                "字",
                AssistantAnswerStream.sections(opening + "字\\uD83D", false, false)
                        .getFirst()
                        .get("content"));
    }

    @Test
    void reorderedSectionsAndNestedUnknownFieldsDoNotExposeProtocol() {
        var sections =
                AssistantAnswerStream.sections(
                        "```json\n"
                            + "{\"sections\":[{\"content\":\"解释\",\"ignored\":{\"content\":\"不能泄漏\"},\"type\":\"GENERAL\"},"
                            + "{\"type\":\"LESSON_CONTEXT\",\"content\":\"下一段",
                        false,
                        true);
        assertEquals(2, sections.size());
        assertEquals("解释", sections.getFirst().get("content"));
        assertEquals("下一段", sections.getLast().get("content"));
        assertFalse(sections.toString().contains("不能泄漏"));
    }

    @Test
    void incompleteProtocolAndUnknownSectionTypesAreWithheld() {
        assertTrue(AssistantAnswerStream.sections("{\"sect", false, false).isEmpty());
        assertTrue(
                AssistantAnswerStream.sections(
                                "{\"sections\":[{\"type\":\"INTERNAL\",\"content\":\"隐藏\"}]}",
                                true,
                                true)
                        .isEmpty());
        assertEquals(
                "GENERAL",
                AssistantAnswerStream.sections(
                                "{\"sections\":[{\"type\":\"COURSE_MATERIAL\",\"content\":\"解释",
                                false,
                                false)
                        .getFirst()
                        .get("type"));
    }
}
