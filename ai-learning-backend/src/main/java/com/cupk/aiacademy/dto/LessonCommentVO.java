package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.LessonComment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LessonCommentVO {
    private Long id;
    private Integer lessonId;
    private Integer userId;
    private Long parentId;
    private String content;
    private String authorName;
    private String authorAvatar;
    private Boolean canDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LessonCommentVO from(LessonComment comment) {
        if (comment == null) {
            return null;
        }
        LessonCommentVO vo = new LessonCommentVO();
        vo.setId(comment.getId());
        vo.setLessonId(comment.getLessonId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getUpdatedAt());
        return vo;
    }
}
