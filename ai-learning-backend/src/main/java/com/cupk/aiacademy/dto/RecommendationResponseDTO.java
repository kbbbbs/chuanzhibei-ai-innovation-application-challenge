package com.cupk.aiacademy.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class RecommendationResponseDTO {
    private String summary;
    private List<String> profileTags = new ArrayList<>();
    private List<RecommendationItemDTO> items = new ArrayList<>();
    private Map<String, Long> counts = new LinkedHashMap<>();
}
