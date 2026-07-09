package com.cupk.aiacademy.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaperMarkResponse {
    private String paperId;
    private String title;
    private String source;
    private boolean favorite;
    private String readStatus;
    private String noteMd;
    private LocalDateTime updatedAt;
}
