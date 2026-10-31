package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.CourseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseCategoryDto {
    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 500)
    private String description;

    @Positive
    private Integer sortOrder;

    public CourseCategory toEntity() {
        CourseCategory category = new CourseCategory();
        category.setName(name);
        category.setDescription(description);
        category.setSortOrder(sortOrder);
        return category;
    }
}
