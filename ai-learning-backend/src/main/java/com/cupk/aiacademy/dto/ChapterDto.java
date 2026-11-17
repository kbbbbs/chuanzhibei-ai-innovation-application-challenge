package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.Chapter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChapterDto {
    @NotNull
    @Positive
    private Integer courseId;

    @NotBlank(message = "章节名称不能为空")
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    @Positive
    private Integer sortOrder;

    public Chapter toEntity() {
        Chapter chapter = new Chapter();
        chapter.setCourseId(courseId);
        chapter.setTitle(title);
        chapter.setDescription(description);
        chapter.setSortOrder(sortOrder);
        return chapter;
    }
}
