package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.HomeworkSubmit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HomeworkSubmitDto {
    @NotNull
    @Positive
    private Integer homeworkId;

    @NotNull
    @Positive
    private Integer studentId;

    private String submitContent;

    @Size(max = 255)
    private String attachmentUrl;

    private LocalDateTime submitTime;

    @DecimalMin("0.00")
    private BigDecimal score;

    private String feedback;

    @Min(0)
    private Integer gradeStatus;

    @Positive
    private Integer graderId;

    private LocalDateTime gradeTime;

    public HomeworkSubmit toEntity() {
        HomeworkSubmit submit = new HomeworkSubmit();
        submit.setHomeworkId(homeworkId);
        submit.setStudentId(studentId);
        submit.setSubmitContent(submitContent);
        submit.setAttachmentUrl(attachmentUrl);
        submit.setSubmitTime(submitTime);
        submit.setScore(score);
        submit.setFeedback(feedback);
        submit.setGradeStatus(gradeStatus);
        submit.setGraderId(graderId);
        submit.setGradeTime(gradeTime);
        return submit;
    }
}
