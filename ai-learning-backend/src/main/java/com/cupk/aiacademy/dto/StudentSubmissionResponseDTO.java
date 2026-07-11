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
public class StudentSubmissionResponseDTO {
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
    private Integer screenSwitchCount;
    private Integer maxScreenSwitches;
    private Boolean cheated;
    private List<StudentAnswerResponse> answers;

    @Getter
    @Setter
    public static class StudentAnswerResponse {
        private Long id;
        private Long paperQuestionId;
        private Long questionId;
        private String questionContent;
        private QuestionType questionType;
        private BigDecimal score;
        private Integer sortOrder;
        private Boolean requiredAnswer;
        private List<OptionItem> options;
        private String answerContent;
        private Boolean correct;
        private BigDecimal obtainedScore;
        private JudgeStatus judgeStatus;
        private String comment;
    }

    @Getter
    @Setter
    public static class OptionItem {
        private String optionKey;
        private String optionContent;
        private Integer sortOrder;
    }
}
