package com.cupk.aiacademy.learning;

import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LearningContentRepository {
    private static final String CONTENT_ROOT = "learning-content/";
    private static final TypeReference<LinkedHashMap<String, Object>> PAGE_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final LearningContentCatalog catalog;
    private final TemplateIndex templateIndex;
    private final Map<String, TemplateRef> templateRefsByKey;
    private final Map<String, Map<String, Object>> pageTemplates = new java.util.concurrent.ConcurrentHashMap<>();

    public LearningContentRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.catalog = loadCatalog();
        this.templateIndex = loadTemplateIndex();
        this.templateRefsByKey = templateRefsByKey(templateIndex);
    }

    LearningProfile profileFor(Course course) {
        String title = course == null ? "" : safe(course.getTitle());
        String description = course == null ? "" : safe(course.getDescription());
        String haystack = (title + " " + description).toLowerCase(Locale.ROOT);
        LearningProfile fallback = null;
        for (LearningProfile profile : catalog.getProfiles()) {
            if ("general".equals(profile.getKey())) fallback = profile;
            if (matches(profile, haystack)) return profile;
        }
        return fallback == null ? catalog.getProfiles().getFirst() : fallback;
    }

    String chapterDescription(LearningProfile profile, String chapterTitle) {
        return render(profile.getChapterDescriptionTemplate(), Map.of("chapterTitle", safe(chapterTitle)));
    }

    Map<String, Object> templateLibrary() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("templates", safeList(templateIndex.getTemplates()));
        return output;
    }

    Map<String, Object> templateByKey(String key) {
        String normalizedKey = safe(key);
        if (!templateRefsByKey.containsKey(normalizedKey)) {
            throw new IllegalArgumentException("未找到互动课本模板");
        }
        return copyPage(pageTemplates.computeIfAbsent(normalizedKey, this::loadTemplatePage));
    }

    Map<String, Object> lessonPage(String chapterTitle, Lesson lesson, boolean unlocked) {
        if (!unlocked) return lockedPage(lesson);

        Map<String, Object> page = parseLessonPage(lesson == null ? "" : lesson.getContent());
        if (page == null) {
            String markdown = firstNonBlank(
                    lesson == null ? "" : lesson.getContent(),
                    resourceText(lesson == null ? "" : lesson.getResourceUrl())
            );
            if (!markdown.isBlank()) {
                page = markdownPage(lessonTitle(lesson), markdown);
            }
        }
        if (page == null && isResourceLesson(lesson)) {
            page = resourcePage(lesson);
        }
        if (page == null) {
            page = emptyPage(lesson);
        }
        return normalizeLessonPage(page, lessonTitle(lesson), chapterTitle);
    }

    String lessonSummary(Lesson lesson) {
        Map<String, Object> page = parseLessonPage(lesson == null ? "" : lesson.getContent());
        if (page != null) {
            String summary = safeString(page.get("summary"));
            if (!summary.isBlank()) return summary;
            String markdown = markdownFromPage(page);
            if (!markdown.isBlank()) return truncate(markdown.replaceAll("[#*`>\\-\\n]+", " ").trim(), 72);
        }
        String resourceType = lesson == null ? "" : safe(lesson.getResourceType());
        if ("video_url".equals(resourceType) || "video_file".equals(resourceType) || "video_link".equals(resourceType)) {
            return "观看视频后完成配套课本和练习";
        }
        if ("ppt".equals(resourceType) || "file_link".equals(resourceType)) {
            return "阅读教师上传的资料并完成课堂任务";
        }
        String text = firstNonBlank(lesson == null ? "" : lesson.getContent(), resourceText(lesson == null ? "" : lesson.getResourceUrl()));
        return text.isBlank() ? "进入本节学习内容" : truncate(text.replaceAll("\\s+", " "), 72);
    }

    List<Map<String, Object>> blocksFromPage(Map<String, Object> page) {
        Object blocks = page == null ? null : page.get("blocks");
        if (!(blocks instanceof List<?> list)) return List.of();
        List<Map<String, Object>> output = new ArrayList<>();
        for (Object block : list) {
            if (block instanceof Map<?, ?> map) {
                output.add(copyObjectMap(map));
            }
        }
        return output;
    }

    String markdownFromPage(Map<String, Object> page) {
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> block : blocksFromPage(page)) {
            if ("markdown".equals(safeString(block.get("type")))) {
                String content = safeString(block.get("content"));
                if (!content.isBlank()) parts.add(content);
            }
        }
        return String.join("\n\n", parts);
    }

    List<String> checkpointsFromPage(Map<String, Object> page) {
        List<String> output = new ArrayList<>();
        for (Map<String, Object> block : blocksFromPage(page)) {
            if (!"checkpoint".equals(safeString(block.get("type")))) continue;
            for (Object item : objectList(block.get("items"))) {
                String text = safeString(item);
                if (!text.isBlank()) output.add(text);
            }
        }
        return output;
    }

    List<LearningExperienceDTO.Experiment> experimentsFromPage(Map<String, Object> page, Integer lessonId) {
        List<LearningExperienceDTO.Experiment> output = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> block : blocksFromPage(page)) {
            if (!"experiment".equals(safeString(block.get("type")))) continue;
            LearningExperienceDTO.Experiment experiment = new LearningExperienceDTO.Experiment();
            experiment.setId(firstNonBlank(safeString(block.get("id")), "lesson-" + lessonId + "-experiment-" + index));
            experiment.setTitle(safeString(block.get("title")));
            experiment.setDescription(safeString(block.get("description")));
            experiment.setKind(firstNonBlank(safeString(block.get("kind")), "cards"));
            experiment.setLessonId(lessonId);
            experiment.setDefaults(blockDefaults(block));
            output.add(experiment);
            index++;
        }
        return output;
    }

    List<LearningExperienceDTO.Challenge> challengesFromPage(Map<String, Object> page, Integer lessonId) {
        List<LearningExperienceDTO.Challenge> output = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> block : blocksFromPage(page)) {
            if (!"challenge".equals(safeString(block.get("type")))) continue;
            LearningExperienceDTO.Challenge challenge = new LearningExperienceDTO.Challenge();
            challenge.setId(firstNonBlank(safeString(block.get("id")), "lesson-" + lessonId + "-challenge-" + index));
            challenge.setTitle(safeString(block.get("title")));
            challenge.setPrompt(safeString(block.get("prompt")));
            challenge.setAnswer(safeString(block.get("answer")));
            challenge.setLessonId(lessonId);
            output.add(challenge);
            index++;
        }
        return output;
    }

    List<LearningExperienceDTO.CodeLab> codeLabsFromPage(Map<String, Object> page, Integer lessonId) {
        List<LearningExperienceDTO.CodeLab> output = new ArrayList<>();
        for (Map<String, Object> block : blocksFromPage(page)) {
            if (!"ide".equals(safeString(block.get("type")))) continue;
            LearningExperienceDTO.CodeLab lab = new LearningExperienceDTO.CodeLab();
            lab.setLessonId(lessonId);
            lab.setLanguage(firstNonBlank(safeString(block.get("language")), "javascript"));
            lab.setTitle(firstNonBlank(safeString(block.get("title")), "在线 IDE"));
            lab.setDescription(safeString(block.get("description")));
            lab.setStarterCode(safeString(block.get("starterCode")));
            lab.setExpectedOutputHint(firstNonBlank(safeString(block.get("expectedOutputHint")), safeString(block.get("hint"))));
            output.add(lab);
        }
        return output;
    }

    private LearningContentCatalog loadCatalog() {
        try (InputStream stream = new ClassPathResource(CONTENT_ROOT + "catalog.json").getInputStream()) {
            LearningContentCatalog loaded = objectMapper.readValue(stream, LearningContentCatalog.class);
            if (loaded.getProfiles() == null || loaded.getProfiles().isEmpty()) {
                throw new IllegalStateException("learning-content/catalog.json must define at least one profile");
            }
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load learning content catalog", e);
        }
    }

    private TemplateIndex loadTemplateIndex() {
        try (InputStream stream = new ClassPathResource(CONTENT_ROOT + "templates/index.json").getInputStream()) {
            TemplateIndex loaded = objectMapper.readValue(stream, TemplateIndex.class);
            if (loaded.getTemplates() == null) loaded.setTemplates(List.of());
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load interactive template index", e);
        }
    }

    private Map<String, TemplateRef> templateRefsByKey(TemplateIndex index) {
        Map<String, TemplateRef> output = new LinkedHashMap<>();
        for (TemplateRef ref : safeList(index.getTemplates())) {
            if (!safe(ref.getKey()).isBlank()) output.put(ref.getKey(), ref);
        }
        return Map.copyOf(output);
    }

    private Map<String, Object> loadTemplatePage(String key) {
        TemplateRef ref = templateRefsByKey.get(key);
        try (InputStream stream = new ClassPathResource(CONTENT_ROOT + "templates/" + ref.getPath()).getInputStream()) {
            Map<String, Object> page = objectMapper.readValue(stream, PAGE_TYPE);
            page.putIfAbsent("key", ref.getKey());
            page.putIfAbsent("title", ref.getTitle());
            page.putIfAbsent("summary", ref.getSummary());
            page.putIfAbsent("estimatedMinutes", ref.getEstimatedMinutes());
            return normalizeLessonPage(page, ref.getTitle(), ref.getSubject());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load interactive template: " + key, e);
        }
    }

    private Map<String, Object> parseLessonPage(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.isBlank()) return null;
        try {
            if (content.startsWith("[")) {
                Map<String, Object> page = new LinkedHashMap<>();
                page.put("blocks", objectMapper.readValue(content, List.class));
                return page;
            }
            Map<String, Object> page = objectMapper.readValue(content, PAGE_TYPE);
            return page.containsKey("blocks") ? page : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> normalizeLessonPage(Map<String, Object> source, String fallbackTitle, String chapterTitle) {
        Map<String, Object> page = copyPage(source);
        page.put("title", firstNonBlank(safeString(page.get("title")), fallbackTitle));
        page.putIfAbsent("chapterTitle", chapterTitle);
        Object blocks = page.get("blocks");
        List<Map<String, Object>> normalizedBlocks = new ArrayList<>();
        if (blocks instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Map<String, Object> block = copyObjectMap(map);
                String type = safeString(block.get("type"));
                block.put("type", type.isBlank() ? "markdown" : type);
                normalizedBlocks.add(block);
            }
        }
        if (normalizedBlocks.isEmpty() && page.get("content") instanceof String content && !content.isBlank()) {
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("type", "markdown");
            markdown.put("content", content);
            normalizedBlocks.add(markdown);
        }
        page.put("blocks", normalizedBlocks);
        if (safeString(page.get("summary")).isBlank()) {
            page.put("summary", truncate(markdownFromPage(page).replaceAll("[#*`>\\-\\n]+", " ").trim(), 120));
        }
        return page;
    }

    private Map<String, Object> markdownPage(String title, String markdown) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "markdown");
        block.put("title", title);
        block.put("content", markdown);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("title", title);
        page.put("summary", truncate(markdown.replaceAll("\\s+", " "), 120));
        page.put("blocks", List.of(block));
        return page;
    }

    private Map<String, Object> resourcePage(Lesson lesson) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "resource");
        block.put("title", lessonTitle(lesson));
        block.put("url", lesson == null ? "" : safeString(lesson.getResourceUrl()));
        block.put("resourceType", lesson == null ? "" : safeString(lesson.getResourceType()));
        block.put("description", "打开教师提供的课程资料，结合本节任务完成学习。");

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("title", lessonTitle(lesson));
        page.put("summary", "本节包含教师提供的课程资料。");
        page.put("blocks", List.of(block));
        return page;
    }

    private Map<String, Object> emptyPage(Lesson lesson) {
        return markdownPage(lessonTitle(lesson), "## 本节内容\n\n教师尚未添加课本内容。你可以先查看本节视频或资料，完成后回到学习地图继续。");
    }

    private Map<String, Object> lockedPage(Lesson lesson) {
        return markdownPage(lessonTitle(lesson), "## 继续学习\n\n完成前置课时后，本节内容会解锁。");
    }

    private Map<String, Object> blockDefaults(Map<String, Object> block) {
        Object defaults = block.get("defaults");
        if (!(defaults instanceof Map<?, ?>)) defaults = block.get("config");
        if (defaults instanceof Map<?, ?> map) return copyObjectMap(map);
        return Map.of();
    }

    private List<Object> objectList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private Map<String, Object> copyPage(Map<String, Object> page) {
        return objectMapper.convertValue(page == null ? Map.of() : page, PAGE_TYPE);
    }

    private Map<String, Object> copyObjectMap(Map<?, ?> map) {
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            output.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return output;
    }

    private boolean isResourceLesson(Lesson lesson) {
        if (lesson == null || safeString(lesson.getResourceUrl()).isBlank()) return false;
        String type = safeString(lesson.getResourceType());
        return "ppt".equals(type) || "file_link".equals(type);
    }

    private String resourceText(String value) {
        String text = value == null ? "" : value.trim();
        return isLikelyUrl(text) ? "" : text;
    }

    private String lessonTitle(Lesson lesson) {
        return firstNonBlank(lesson == null ? "" : lesson.getTitle(), "课时学习");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String text = safeString(value);
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        String text = safeString(value);
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private boolean matches(LearningProfile profile, String haystack) {
        if (profile.getKeywords() == null || profile.getKeywords().isEmpty()) return false;
        for (String keyword : profile.getKeywords()) {
            if (haystack.contains(safe(keyword).toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String render(String template, Map<String, String> vars) {
        String output = safe(template);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isLikelyUrl(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//");
    }

    @Data
    static class LearningContentCatalog {
        private List<LearningProfile> profiles;
    }

    @Data
    static class TemplateIndex {
        private List<TemplateRef> templates;
    }

    @Data
    static class TemplateRef {
        private String key;
        private String title;
        private String subject;
        private String summary;
        private String path;
        private List<String> tags;
        private Integer estimatedMinutes;
    }

    @Data
    static class LearningProfile {
        private String key;
        private String label;
        private String tagline;
        private String audience;
        private String finalProject;
        private String chapterDescriptionTemplate;
        private List<String> keywords;
        private List<String> outcomes;
    }
}
