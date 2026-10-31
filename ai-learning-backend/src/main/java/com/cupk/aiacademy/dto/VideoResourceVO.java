package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.VideoResource;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoResourceVO {
    private Long id;
    private String sourceUrl;
    private String title;
    private String provider;
    private String status;
    private String playUrl;
    private String coverUrl;
    private Integer duration;
    private Long fileSize;
    private String summaryMd;
    private String errorMessage;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VideoResourceVO from(VideoResource resource) {
        if (resource == null) {
            return null;
        }
        VideoResourceVO vo = new VideoResourceVO();
        vo.setId(resource.getId());
        vo.setSourceUrl(resource.getSourceUrl());
        vo.setTitle(resource.getTitle());
        vo.setProvider(resource.getProvider());
        vo.setStatus(resource.getStatus());
        vo.setPlayUrl(resource.getPlayUrl());
        vo.setCoverUrl(resource.getCoverUrl());
        vo.setDuration(resource.getDuration());
        vo.setFileSize(resource.getFileSize());
        vo.setSummaryMd(resource.getSummaryMd());
        vo.setErrorMessage(resource.getErrorMessage());
        vo.setCreatedBy(resource.getCreatedBy());
        vo.setCreatedAt(resource.getCreatedAt());
        vo.setUpdatedAt(resource.getUpdatedAt());
        return vo;
    }
}
