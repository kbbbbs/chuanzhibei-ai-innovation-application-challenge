package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.ClassStudent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ClassStudentDto {
    @NotNull
    @Positive
    private Integer classId;

    @NotNull
    @Positive
    private Integer studentId;

    @Min(0)
    private Integer status;

    public ClassStudent toEntity() {
        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(classId);
        classStudent.setStudentId(studentId);
        classStudent.setStatus(status);
        return classStudent;
    }
}
