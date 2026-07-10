package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.JudgeStatus;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.SubmissionStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SubmissionResponseDTO {
    private Long id;
    private Long examId;
    private String examName;
    private String candidateId;
    private Long studentId;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private BigDecimal totalScore;
    private SubmissionStatus status;
    private List<SubmissionAnswerResponse> answers;

    @Getter
    @Setter
    public static class SubmissionAnswerResponse {
        private Long id;
        private Long paperQuestionId;
        private Long questionId;
        private String questionContent;
        private QuestionType questionType;
        private String answerContent;
        private String referenceAnswer;
        private BigDecimal maxScore;
        private Boolean correct;
        private BigDecimal score;
        private JudgeStatus judgeStatus;
        private String comment;
    }
}
