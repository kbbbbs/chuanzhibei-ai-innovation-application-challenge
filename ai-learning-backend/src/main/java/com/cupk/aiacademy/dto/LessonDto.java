package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.Lesson;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LessonDto {
    @NotNull
    @Positive
    private Integer chapterId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 30)
    private String resourceType;

    @Size(max = 255)
    private String resourceUrl;

    private String content;

    @Size(max = 255)
    private String videoUrl;

    @Positive
    private Long videoResourceId;

    @Min(0)
    private Integer duration;

    @Positive
    private Integer sortOrder;

    @Min(0)
    private Integer viewCount;

    @Min(0)
    private Integer status;

    public Lesson toEntity() {
        Lesson lesson = new Lesson();
        lesson.setChapterId(chapterId);
        lesson.setTitle(title);
        lesson.setResourceType(resourceType);
        lesson.setResourceUrl(resourceUrl);
        lesson.setContent(content);
        lesson.setVideoUrl(videoUrl);
        lesson.setVideoResourceId(videoResourceId);
        lesson.setDuration(duration);
        lesson.setSortOrder(sortOrder);
        lesson.setViewCount(viewCount);
        lesson.setStatus(status);
        return lesson;
    }
}
