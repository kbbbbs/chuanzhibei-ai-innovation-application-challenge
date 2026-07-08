package com.cupk.aiacademy.teacherapply;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeacherApplicationReviewRequest {
    @Min(1)
    @Max(2)
    private Integer status;
    @Size(max = 255)
    private String reviewComment;
}
