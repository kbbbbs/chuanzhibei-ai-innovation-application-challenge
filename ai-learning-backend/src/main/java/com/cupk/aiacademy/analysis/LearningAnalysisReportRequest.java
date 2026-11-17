package com.cupk.aiacademy.analysis;

import jakarta.validation.constraints.*;

public record LearningAnalysisReportRequest(
        @NotNull @Positive Integer courseId,
        @NotBlank @Size(max=36) String templateId,
        @NotNull @Min(300) @Max(5000) Integer targetWords,
        @NotBlank @Size(max=100) String requestId) {}
