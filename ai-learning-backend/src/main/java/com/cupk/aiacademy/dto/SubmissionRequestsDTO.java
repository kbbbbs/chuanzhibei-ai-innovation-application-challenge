package com.cupk.aiacademy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

public class SubmissionRequestsDTO {

    @Getter
    @Setter
    public static class StartSubmissionRequest {
        @NotBlank
        @Size(max = 64)
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

        private List<String> selectedOptionKeys;

        @Size(max = 2000)
        private String answerContent;
    }

    @Getter
    @Setter
    public static class ManualGradeRequest {
        @Positive
        private Long graderId;

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
        @Size(max = 1000)
        private String comment;
    }
}
