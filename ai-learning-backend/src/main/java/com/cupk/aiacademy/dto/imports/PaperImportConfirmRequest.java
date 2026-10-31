package com.cupk.aiacademy.dto.imports;

import com.cupk.aiacademy.common.PaperStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class PaperImportConfirmRequest {
    @NotBlank
    private String name;

    @NotNull
    @Positive
    private Long courseId;

    @NotNull
    @Positive
    private Integer durationMinutes;

    @NotNull
    private PaperStatus status;

    @NotNull
    private List<ImportPreviewItem> items;
}
