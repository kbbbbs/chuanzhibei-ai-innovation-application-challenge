// 文件: src/main/java/com/cupk/aiacademy/dto/paper/PaperRequest.java
package com.cupk.aiacademy.dto.paper;

import com.cupk.aiacademy.common.PaperStatus;
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
public class PaperRequest {
    @NotBlank
    @Size(max = 200)
    private String name;
    @Size(max = 1000)
    private String description;
    @NotNull
    @Positive
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
        @DecimalMin("0.01")
        private BigDecimal score;
        @NotNull
        private Boolean requiredAnswer;
    }
}
