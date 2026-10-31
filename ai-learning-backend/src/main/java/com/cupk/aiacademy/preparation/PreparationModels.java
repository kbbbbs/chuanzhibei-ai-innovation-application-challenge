package com.cupk.aiacademy.preparation;

import com.cupk.aiacademy.dto.QuestionRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class PreparationModels {
    private PreparationModels() {
    }

    @Data
    public static class Config {
        @NotBlank
        private String title;
        @NotNull
        private Integer categoryId;
        private String description;
        private String difficulty = "beginner";
        @NotBlank
        private String audience;
        @Min(1)
        @Max(1000)
        private Integer totalHours = 32;
        @Min(1)
        @Max(20)
        private Integer chapterCount = 6;
        @Min(1)
        @Max(10)
        private Integer lessonsPerChapter = 3;
        private String teachingGoals;
        private String extraInstructions;
        @Valid
        private QuestionConfig questionConfig = new QuestionConfig();
        private List<String> knowledgeBaseIds = new ArrayList<>();
    }

    @Data
    public static class QuestionConfig {
        @Min(1)
        @Max(20)
        private Integer countPerChapter = 5;
        private List<String> types = new ArrayList<>(List.of("SINGLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER"));
        private String difficulty = "MEDIUM";
        private String instructions;
    }

    @Data
    public static class Outline {
        private String courseDescription;
        private List<ChapterDraft> chapters = new ArrayList<>();
    }

    @Data
    public static class ChapterDraft {
        private String chapterKey;
        private String title;
        private String description;
        private Integer sortOrder;
        private List<LessonDraft> lessons = new ArrayList<>();
    }

    @Data
    public static class LessonDraft {
        private String lessonKey;
        private String title;
        private String objectives;
        private List<String> knowledgePoints = new ArrayList<>();
        private Integer durationMinutes;
        private Integer sortOrder;
        private String contentMd;
    }

    @Data
    public static class ChapterQuestions {
        private String chapterKey;
        private String chapterTitle;
        @Valid
        private List<QuestionRequestDTO> questions = new ArrayList<>();
    }

    @Data
    public static class SourceFile {
        private String name;
        private String knowledgeId;
        private String parseStatus;
        private Integer parseProgress;
        private String errorMessage;
    }

    @Data
    public static class View {
        private Long id;
        private Integer teacherId;
        private String title;
        private Config config;
        private List<String> knowledgeBaseIds = new ArrayList<>();
        private String ownedKnowledgeBaseId;
        private List<SourceFile> sourceFiles = new ArrayList<>();
        private boolean sourcesReady;
        private Outline outline;
        private List<LessonDraft> lessons = new ArrayList<>();
        private List<ChapterQuestions> questions = new ArrayList<>();
        private String projectStatus;
        private String currentStage;
        private String generationStatus;
        private Integer progress;
        private String errorMessage;
        private Integer publishedCourseId;
        private Integer version;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    public static class PublishResult {
        private Integer courseId;
        private Integer chaptersCreated;
        private Integer lessonsCreated;
        private Integer questionsCreated;
    }
}
