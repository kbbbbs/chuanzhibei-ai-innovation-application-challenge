package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.GradeRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradeRecordDto {
    @NotNull
    @Positive
    private Integer studentId;

    @NotNull
    @Positive
    private Integer classId;

    @NotNull
    @Positive
    private Integer courseId;

    @NotNull
    @Positive
    private Integer teacherId;

    @NotBlank
    @Size(max = 100)
    private String gradeName;

    @NotBlank
    @Size(max = 30)
    private String sourceType;

    @Positive
    private Integer sourceId;

    @DecimalMin("0.00")
    private BigDecimal score;

    @DecimalMin("0.00")
    private BigDecimal fullScore;

    @Size(max = 1000)
    private String comment;

    public GradeRecord toEntity() {
        GradeRecord grade = new GradeRecord();
        grade.setStudentId(studentId);
        grade.setClassId(classId);
        grade.setCourseId(courseId);
        grade.setTeacherId(teacherId);
        grade.setGradeName(gradeName);
        grade.setSourceType(sourceType);
        grade.setSourceId(sourceId);
        grade.setScore(score);
        grade.setFullScore(fullScore);
        grade.setComment(comment);
        return grade;
    }
}
