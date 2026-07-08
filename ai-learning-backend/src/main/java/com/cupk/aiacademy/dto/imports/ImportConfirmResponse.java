package com.cupk.aiacademy.dto.imports;

import lombok.Data;

import java.util.List;

@Data
public class ImportConfirmResponse {
    private int total;
    private int success;
    private int fail;
    private List<ImportConfirmItem> items;

    public ImportConfirmResponse(List<ImportConfirmItem> items) {
        this.items = items == null ? List.of() : items;
        this.total = this.items.size();
        this.success = (int) this.items.stream().filter(item -> Boolean.TRUE.equals(item.getSuccess())).count();
        this.fail = total - success;
    }
}
