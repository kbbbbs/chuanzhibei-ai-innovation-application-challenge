package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.AdministrativeClass;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdministrativeClassDto {
    @NotNull
    @Positive
    private Integer courseId;

    @NotBlank
    @Size(max = 50)
    private String classCode;

    @NotBlank
    @Size(max = 100)
    private String className;

    @Size(max = 50)
    private String grade;

    @Size(max = 100)
    private String major;

    @Size(max = 1000)
    private String description;

    @Min(0)
    private Integer status;

    public AdministrativeClass toEntity() {
        AdministrativeClass administrativeClass = new AdministrativeClass();
        administrativeClass.setCourseId(courseId);
        administrativeClass.setClassCode(classCode);
        administrativeClass.setClassName(className);
        administrativeClass.setGrade(grade);
        administrativeClass.setMajor(major);
        administrativeClass.setDescription(description);
        administrativeClass.setStatus(status);
        return administrativeClass;
    }
}
