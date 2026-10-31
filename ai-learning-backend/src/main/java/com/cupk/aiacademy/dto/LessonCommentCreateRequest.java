package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LessonCommentCreateRequest {
    @NotBlank
    @Size(max = 1000)
    private String content;
}
