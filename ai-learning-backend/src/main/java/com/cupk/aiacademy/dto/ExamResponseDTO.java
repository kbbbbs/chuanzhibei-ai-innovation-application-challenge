package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.AssessmentType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamResponseDTO {
    private Long id;
    private Long classId;
    private Long teacherId;
    private String name;
    private Long paperId;
    private String paperName;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private ExamStatus status;
    private AssessmentType assessmentType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
