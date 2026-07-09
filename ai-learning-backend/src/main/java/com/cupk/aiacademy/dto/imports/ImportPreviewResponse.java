package com.cupk.aiacademy.dto.imports;

import lombok.Data;

import java.util.List;

@Data
public class ImportPreviewResponse {
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private List<ImportPreviewItem> items;

    public ImportPreviewResponse(List<ImportPreviewItem> items) {
        this.items = items == null ? List.of() : items;
        this.totalRows = this.items.size();
        this.validRows = (int) this.items.stream().filter(item -> Boolean.TRUE.equals(item.getValid())).count();
        this.invalidRows = totalRows - validRows;
    }
}
