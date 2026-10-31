package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.Notice;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoticeDto {
    @Positive
    private Integer classId;

    @NotBlank
    @Size(max = 100)
    private String title;

    private String content;

    @Size(max = 30)
    private String noticeType;

    @Min(0)
    private Integer status;

    public Notice toEntity() {
        Notice notice = new Notice();
        notice.setClassId(classId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setNoticeType(noticeType);
        notice.setStatus(status);
        return notice;
    }
}
