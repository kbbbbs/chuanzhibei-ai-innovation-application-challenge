// 文件: src/main/java/com/cupk/aiacademy/dto/exam/ExamResponse.java
package com.cupk.aiacademy.dto.exam;

import com.cupk.aiacademy.common.ExamStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamResponse {
    private Long id;
    private String name;
    private Long paperId;
    private String paperName;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private ExamStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
