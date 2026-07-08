package com.cupk.aiacademy.dto.imports;

import lombok.Data;

import java.util.List;

@Data
public class ImportConfirmRequest {
    private List<ImportPreviewItem> items;
}
