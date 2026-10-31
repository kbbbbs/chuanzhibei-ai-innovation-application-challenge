package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HomeCourseRecommendationRequest {
    @NotNull(message = "请选择课程")
    private Integer courseId;

    @Size(max = 255, message = "推荐理由不能超过255个字符")
    private String reason;

    @Min(value = 1, message = "排序值不能小于1")
    private Integer sortOrder;

    private Integer status;
}
