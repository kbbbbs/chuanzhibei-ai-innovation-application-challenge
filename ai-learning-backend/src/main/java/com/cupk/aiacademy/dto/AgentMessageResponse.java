package com.cupk.aiacademy.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentMessageResponse {
    private Integer id;
    private Integer sessionId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
