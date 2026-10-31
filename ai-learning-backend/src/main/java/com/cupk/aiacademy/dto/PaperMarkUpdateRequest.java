package com.cupk.aiacademy.dto;

import lombok.Data;

@Data
public class PaperMarkUpdateRequest {
    private Boolean favorite;
    private String readStatus;
    private String noteMd;
    private String title;
    private String source;
}
