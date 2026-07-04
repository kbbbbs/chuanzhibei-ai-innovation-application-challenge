package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
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

@Getter
@Setter
public class QuestionRequestDTO {
    private Long courseId;
    private Long teacherId;
    @NotBlank
    @Size(max = 500)
    private String content;
    @NotNull
    private QuestionType type;
    @NotNull
    private Difficulty difficulty;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal score;
    @Size(max = 200)
    private String knowledgePoint;
    @Size(max = 1000)
    private String analysis;
    @NotNull
    private RecordStatus status;
    @Valid
    private List<QuestionOptionRequest> options;
    @Valid
    @NotEmpty
    private List<QuestionAnswerRequest> answers;

    @Getter
    @Setter
    public static class QuestionOptionRequest {
        @NotBlank
        @Size(max = 8)
        private String optionKey;
        @NotBlank
        @Size(max = 300)
        private String optionContent;
        @NotNull
        @Positive
        private Integer sortOrder;
    }

    @Getter
    @Setter
    public static class QuestionAnswerRequest {
        @NotBlank
        @Size(max = 500)
        private String answerContent;
        @NotNull
        @Positive
        private Integer sortOrder;
    }
}
