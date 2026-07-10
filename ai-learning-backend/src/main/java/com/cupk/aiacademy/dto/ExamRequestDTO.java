package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.ExamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamRequestDTO {
    private Long classId;
    private Long teacherId;
    @NotBlank
    @Size(max = 200)
    private String name;
    @NotNull
    private Long paperId;
    @Size(max = 1000)
    private String description;
    @NotNull
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @NotNull
    @Positive
    private Integer durationMinutes;
    @NotNull
    private ExamStatus status;
}
