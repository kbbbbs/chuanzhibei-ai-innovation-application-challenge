package com.cupk.aiacademy.dto.imports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewItem {
    private Integer rowNumber;
    private Boolean valid;
    private List<String> errors = new ArrayList<>();
    private Object normalizedData;

    public static ImportPreviewItem valid(Integer rowNumber, Object normalizedData) {
        return new ImportPreviewItem(rowNumber, true, new ArrayList<>(), normalizedData);
    }

    public static ImportPreviewItem invalid(Integer rowNumber, List<String> errors, Object normalizedData) {
        return new ImportPreviewItem(rowNumber, false, errors == null ? new ArrayList<>() : errors, normalizedData);
    }
}
