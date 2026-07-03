package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.PaperStatus;
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
public class PaperRequestDTO {
    private Long courseId;
    private Long teacherId;
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private Integer durationMinutes;
    @NotNull
    private PaperStatus status;
    @Valid
    @NotEmpty
    private List<PaperQuestionRequest> questions;

    @Getter
    @Setter
    public static class PaperQuestionRequest {
        @NotNull
        private Long questionId;
        @NotNull
        private Integer sortOrder;
        @NotNull
        @DecimalMin("0")
        private BigDecimal score;
        @NotNull
        private Boolean requiredAnswer;
    }
}
