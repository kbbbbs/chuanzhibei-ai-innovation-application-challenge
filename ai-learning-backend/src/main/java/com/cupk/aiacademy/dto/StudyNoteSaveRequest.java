package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudyNoteSaveRequest {
    @Size(max = 255)
    private String title;

    private String contentMd;

    @Min(0)
    private Integer lastVideoTime;
}
