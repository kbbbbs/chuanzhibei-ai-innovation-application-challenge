package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.Homework;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HomeworkDto {
    @NotNull
    @Positive
    private Integer classId;

    @NotNull
    @Positive
    private Integer teacherId;

    @NotBlank
    @Size(max = 100)
    private String title;

    private String content;

    @Size(max = 255)
    private String attachmentUrl;

    @DecimalMin("0.00")
    private BigDecimal fullScore;

    private LocalDateTime startTime;
    private LocalDateTime deadline;

    @Min(0)
    private Integer status;

    public Homework toEntity() {
        Homework homework = new Homework();
        homework.setClassId(classId);
        homework.setTeacherId(teacherId);
        homework.setTitle(title);
        homework.setContent(content);
        homework.setAttachmentUrl(attachmentUrl);
        homework.setFullScore(fullScore);
        homework.setStartTime(startTime);
        homework.setDeadline(deadline);
        homework.setStatus(status);
        return homework;
    }
}
