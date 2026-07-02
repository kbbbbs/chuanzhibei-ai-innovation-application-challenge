package com.example.final_exam_question_bank.question.dto;

import com.example.final_exam_question_bank.common.Difficulty;
import com.example.final_exam_question_bank.common.QuestionType;
import com.example.final_exam_question_bank.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class QuestionResponse {
    private Long id;
    private String content;
    private QuestionType type;
    private Difficulty difficulty;
    private BigDecimal score;
    private String knowledgePoint;
    private String analysis;
    private RecordStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OptionResponse> options;
    private List<AnswerResponse> answers;

    @Getter
    @Setter
    public static class OptionResponse {
        private Long id;
        private String optionKey;
        private String optionContent;
        private Integer sortOrder;
    }

    @Getter
    @Setter
    public static class AnswerResponse {
        private Long id;
        private String answerContent;
        private Integer sortOrder;
    }
}
