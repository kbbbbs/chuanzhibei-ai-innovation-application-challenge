package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.TeachingClass;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeachingClassDto {
    @Positive
    private Integer courseId;

    @NotNull
    @Positive
    private Integer teacherId;

    @NotBlank
    @Size(max = 100)
    private String className;

    @Size(max = 50)
    private String semester;

    @Size(max = 1000)
    private String description;

    @Min(0)
    private Integer status;

    public static TeachingClassDto fromEntity(TeachingClass teachingClass) {
        TeachingClassDto dto = new TeachingClassDto();
        dto.setCourseId(teachingClass.getCourseId());
        dto.setTeacherId(teachingClass.getTeacherId());
        dto.setClassName(teachingClass.getClassName());
        dto.setSemester(teachingClass.getSemester());
        dto.setDescription(teachingClass.getDescription());
        dto.setStatus(teachingClass.getStatus());
        return dto;
    }

    public TeachingClass toEntity() {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setCourseId(courseId);
        teachingClass.setTeacherId(teacherId);
        teachingClass.setClassName(className);
        teachingClass.setSemester(semester);
        teachingClass.setDescription(description);
        teachingClass.setStatus(status);
        return teachingClass;
    }
}
