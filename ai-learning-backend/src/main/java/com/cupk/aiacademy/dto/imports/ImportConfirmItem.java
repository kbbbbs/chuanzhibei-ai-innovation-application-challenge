package com.cupk.aiacademy.dto.imports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportConfirmItem {
    private Integer rowNumber;
    private Boolean success;
    private String error;
    private Object result;

    public static ImportConfirmItem success(Integer rowNumber, Object result) {
        return new ImportConfirmItem(rowNumber, true, null, result);
    }

    public static ImportConfirmItem failure(Integer rowNumber, String error, Object result) {
        return new ImportConfirmItem(rowNumber, false, error, result);
    }
}
