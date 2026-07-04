package com.cupk.aiacademy.weknora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeKnoraSearchRequest {
    private List<String> knowledgeBaseIds;
    private String query;
}
