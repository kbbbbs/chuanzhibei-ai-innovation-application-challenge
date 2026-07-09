package com.cupk.aiacademy.learning;

import com.cupk.aiacademy.pojo.Lesson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningContentRepositoryTest {
    private final LearningContentRepository repository = new LearningContentRepository(new ObjectMapper());

    @Test
    void loadsBuiltInInteractiveTemplate() {
        Map<String, Object> library = repository.templateLibrary();
        List<?> templates = (List<?>) library.get("templates");

        Map<String, Object> page = repository.templateByKey("ml.gradient-descent");

        assertTrue(templates.size() >= 6);
        assertEquals("梯度下降：学习率与收敛", page.get("title"));
        assertTrue(((List<?>) page.get("blocks")).stream().anyMatch(block ->
                block instanceof Map<?, ?> map && "ide".equals(map.get("type"))));
    }

    @Test
    void wrapsMarkdownLessonAsPageBlocks() {
        Lesson lesson = new Lesson();
        lesson.setTitle("Markdown 课时");
        lesson.setResourceType("markdown");
        lesson.setContent("## 核心讲解\n\n这是一节由教师维护的 Markdown。");

        Map<String, Object> page = repository.lessonPage("第一章", lesson, true);

        assertEquals("Markdown 课时", page.get("title"));
        assertEquals(1, repository.blocksFromPage(page).size());
        assertTrue(repository.markdownFromPage(page).contains("教师维护"));
    }

}
