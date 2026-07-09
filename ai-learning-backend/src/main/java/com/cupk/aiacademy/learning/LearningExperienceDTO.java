package com.cupk.aiacademy.learning;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LearningExperienceDTO {
    private CourseOverview overview;
    private List<PathNode> path;
    private List<TextbookChapter> textbook;
    private List<Experiment> experiments;
    private List<Challenge> challenges;
    private List<CodeLab> codeLabs;
    private CodeLab codeLab;

    @Data
    public static class PathNode {
        private Integer id;
        private String title;
        private String chapterTitle;
        private String type;
        private Integer progress;
        private boolean unlocked;
        private String summary;
        private Integer duration;
    }

    @Data
    public static class CourseOverview {
        private String title;
        private String domain;
        private String tagline;
        private String audience;
        private String finalProject;
        private Integer totalLessons;
        private Integer estimatedMinutes;
        private List<String> outcomes;
    }

    @Data
    public static class TextbookChapter {
        private Integer id;
        private String title;
        private String description;
        private List<TextbookLesson> lessons;
    }

    @Data
    public static class TextbookLesson {
        private Integer id;
        private String title;
        private String resourceType;
        private String resourceUrl;
        private String content;
        private Map<String, Object> page;
        private List<Map<String, Object>> blocks;
        private Integer duration;
        private Integer progress;
        private boolean unlocked;
        private List<String> checkpoints;
    }

    @Data
    public static class Experiment {
        private String id;
        private String title;
        private String description;
        private String kind;
        private Integer lessonId;
        private Map<String, Object> defaults;
    }

    @Data
    public static class Challenge {
        private String id;
        private String title;
        private String prompt;
        private String answer;
        private Integer lessonId;
    }

    @Data
    public static class CodeLab {
        private Integer lessonId;
        private String language;
        private String title;
        private String description;
        private String starterCode;
        private String expectedOutputHint;
    }
}
