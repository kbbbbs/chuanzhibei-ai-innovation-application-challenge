package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.LearningProgress;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningProgressDto {
    @NotNull
    @Positive
    private Integer classId;

    @NotNull
    @Positive
    private Integer studentId;

    @NotNull
    @Positive
    private Integer lessonId;

    @Min(0)
    @Max(100)
    private Integer progress;

    @Min(0)
    private Integer learnedSeconds;

    @Min(0)
    private Integer lastPosition;

    private LocalDateTime lastLearnTime;

    public LearningProgress toEntity() {
        LearningProgress progressEntity = new LearningProgress();
        progressEntity.setClassId(classId);
        progressEntity.setStudentId(studentId);
        progressEntity.setLessonId(lessonId);
        progressEntity.setProgress(progress);
        progressEntity.setLearnedSeconds(learnedSeconds);
        progressEntity.setLastPosition(lastPosition);
        progressEntity.setLastLearnTime(lastLearnTime);
        return progressEntity;
    }
}
