package com.cupk.aiacademy.common;

import com.cupk.aiacademy.dto.PaperPreflightResponse;
import lombok.Getter;

@Getter
public class PaperPreflightWarningException extends RuntimeException {
    private final PaperPreflightResponse diagnostics;

    public PaperPreflightWarningException(PaperPreflightResponse diagnostics) {
        super("试卷存在需确认的警告");
        this.diagnostics = diagnostics;
    }
}
