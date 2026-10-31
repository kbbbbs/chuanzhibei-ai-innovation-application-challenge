package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.AdministrativeClassStudent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AdministrativeClassStudentDto {
    @NotNull
    @Positive
    private Integer studentId;

    @Min(0)
    private Integer status;

    public AdministrativeClassStudent toEntity(Integer administrativeClassId) {
        AdministrativeClassStudent student = new AdministrativeClassStudent();
        student.setAdministrativeClassId(administrativeClassId);
        student.setStudentId(studentId);
        student.setStatus(status == null ? 1 : status);
        return student;
    }
}
