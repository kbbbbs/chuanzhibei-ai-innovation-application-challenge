package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.QuestionType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PaperPreflightResponse {
    private BigDecimal totalScore;
    private List<QuestionType> expectedTypes;
    private List<QuestionType> presentTypes;
    private List<QuestionType> missingTypes;
    private List<String> warningCodes;

    public boolean hasWarnings() {
        return warningCodes != null && !warningCodes.isEmpty();
    }
}
