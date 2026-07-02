package com.example.final_exam_question_bank.question.dto;

import com.example.final_exam_question_bank.common.Difficulty;
import com.example.final_exam_question_bank.common.QuestionType;
import com.example.final_exam_question_bank.common.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class QuestionRequest {
    @NotBlank
    private String content;
    @NotNull
    private QuestionType type;
    @NotNull
    private Difficulty difficulty;
    @NotNull
    @DecimalMin("0")
    private BigDecimal score;
    private String knowledgePoint;
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
        private String optionKey;
        @NotBlank
        private String optionContent;
        @NotNull
        private Integer sortOrder;
    }

    @Getter
    @Setter
    public static class QuestionAnswerRequest {
        @NotBlank
        private String answerContent;
        @NotNull
        private Integer sortOrder;
    }
}
