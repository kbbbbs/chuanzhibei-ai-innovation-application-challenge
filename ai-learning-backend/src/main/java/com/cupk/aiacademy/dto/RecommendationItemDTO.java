package com.cupk.aiacademy.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RecommendationItemDTO {
    private String id;
    private String type;
    private Integer itemId;
    private String title;
    private String subtitle;
    private String description;
    private String reason;
    private String targetUrl;
    private String actionText;
    private double score;
    private Map<String, Object> meta = new LinkedHashMap<>();
}
