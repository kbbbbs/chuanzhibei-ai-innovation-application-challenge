package com.cupk.aiacademy.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HomeCourseRecommendationVO {
    private Integer id;
    private Integer courseId;
    private Integer classId;
    private String className;
    private String courseTitle;
    private String courseDescription;
    private String coverUrl;
    private String teacher;
    private String difficulty;
    private Integer courseStatus;
    private String reason;
    private Integer sortOrder;
    private Integer status;
    private Boolean enrolled;
    private Boolean joinable;
    private LocalDateTime updateTime;
    private String targetUrl;
}
