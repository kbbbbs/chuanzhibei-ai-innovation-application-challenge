// 文件: src/main/java/com/cupk/aiacademy/dto/exam/ExamRequest.java
package com.cupk.aiacademy.dto.exam;

import com.cupk.aiacademy.common.ExamStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamRequest {
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
