package com.cupk.aiacademy.dto;

import lombok.Data;

@Data
public class LessonPlaybackDto {
    private Integer lessonId;
    private String title;
    private String status;
    private String playUrl;
    private Integer duration;
    private String summaryMd;
    private String errorMessage;
    private Long videoResourceId;
}
