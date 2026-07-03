package com.cupk.aiacademy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

public class SubmissionRequestsDTO {

    @Getter
    @Setter
    public static class StartSubmissionRequest {
        @NotBlank
        private String candidateId;
    }

    @Getter
    @Setter
    public static class SaveAnswerRequest {
        @NotEmpty
        @Valid
        private List<AnswerItem> answers;
    }

    @Getter
    @Setter
    public static class AnswerItem {
        @NotNull
        private Long paperQuestionId;
        private String answerContent;
        private List<String> selectedOptionKeys;
    }

    @Getter
    @Setter
    public static class ManualGradeRequest {
        @NotEmpty
        @Valid
        private List<ManualGradeItem> grades;
    }

    @Getter
    @Setter
    public static class ManualGradeItem {
        @NotNull
        private Long submissionAnswerId;
        @NotNull
        @DecimalMin("0")
        private BigDecimal score;
        private String comment;
    }
}
