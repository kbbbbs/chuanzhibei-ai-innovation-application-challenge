package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.common.ExamStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamRequestdto {
    @NotBlank
    private String name;
    @NotNull
    private Long paperId;
    private String description;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    @Future
    private LocalDateTime endTime;
    @NotNull
    private Integer durationMinutes;
    @NotNull
    private ExamStatus status;
}
