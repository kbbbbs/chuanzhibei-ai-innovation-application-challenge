package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.SubmissionStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ExamResultResponseDTO {
    private Long examId;
    private String examName;
    private ExamStatus status;
    private BigDecimal fullScore;
    private BigDecimal averageScore;
    private BigDecimal passRate;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private List<StudentScore> studentScores;

    @Getter
    @Setter
    public static class StudentScore {
        private Long studentId;
        private String studentName;
        private Long submissionId;
        private BigDecimal totalScore;
        private SubmissionStatus submissionStatus;
        private boolean absent;
    }
}
