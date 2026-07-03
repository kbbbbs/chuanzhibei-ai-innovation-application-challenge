package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.PaperStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class PaperResponseDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal totalScore;
    private Integer durationMinutes;
    private PaperStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PaperQuestionResponse> paperQuestions;

    @Getter
    @Setter
    public static class PaperQuestionResponse {
        private Long id;
        private Long questionId;
        private String questionContent;
        private Integer sortOrder;
        private BigDecimal score;
        private Boolean requiredAnswer;
    }
}
