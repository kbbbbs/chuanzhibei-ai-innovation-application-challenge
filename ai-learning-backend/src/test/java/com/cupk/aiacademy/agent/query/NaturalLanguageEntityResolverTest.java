package com.cupk.aiacademy.agent.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalLanguageEntityResolverTest {
    @Test
    void extractTermsKeepsCourseNameFromDailyQuestion() {
        NaturalLanguageEntityResolver resolver = new NaturalLanguageEntityResolver(
                null, null, null, null, null, null
        );

        List<String> terms = resolver.extractTerms("现在有没有一门叫Java 入门的课");

        assertEquals("Java 入门", terms.getFirst());
        assertTrue(terms.contains("Java"));
        assertTrue(terms.contains("入门"));
    }
}
