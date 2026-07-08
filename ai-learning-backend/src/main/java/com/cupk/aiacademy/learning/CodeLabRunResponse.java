package com.cupk.aiacademy.learning;

import lombok.Data;

@Data
public class CodeLabRunResponse {
    private String stdout;
    private String stderr;
    private int exitCode;
    private boolean timedOut;
}
