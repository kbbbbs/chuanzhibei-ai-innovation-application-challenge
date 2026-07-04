package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.Course;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseDto {
    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    @Size(max = 255)
    private String cover;

    @Size(max = 255)
    private String coverUrl;

    @Size(max = 50)
    private String teacher;

    @Size(max = 20)
    private String difficulty;

    @NotNull
    @Positive
    private Integer categoryId;

    @Positive
    private Integer teacherId;

    @Min(0)
    private Integer studentCount;

    @Min(0)
    private Integer viewCount;

    @DecimalMin("0.00")
    @DecimalMax("5.00")
    private BigDecimal rating;

    @Min(0)
    private Integer status;

    public Course toEntity() {
        Course course = new Course();
        course.setTitle(title);
        course.setDescription(description);
        course.setCover(cover);
        course.setCoverUrl(coverUrl);
        course.setTeacher(teacher);
        course.setDifficulty(difficulty);
        course.setCategoryId(categoryId);
        course.setTeacherId(teacherId);
        course.setStudentCount(studentCount);
        course.setViewCount(viewCount);
        course.setRating(rating);
        course.setStatus(status);
        return course;
    }
}
