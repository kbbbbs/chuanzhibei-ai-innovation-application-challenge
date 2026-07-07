package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.StudyNote;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudyNoteVO {
    private Long id;
    private Integer userId;
    private Integer courseId;
    private Integer lessonId;
    private String title;
    private String contentMd;
    private Integer lastVideoTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudyNoteVO from(StudyNote note) {
        if (note == null) {
            return null;
        }
        StudyNoteVO vo = new StudyNoteVO();
        vo.setId(note.getId());
        vo.setUserId(note.getUserId());
        vo.setCourseId(note.getCourseId());
        vo.setLessonId(note.getLessonId());
        vo.setTitle(note.getTitle());
        vo.setContentMd(note.getContentMd());
        vo.setLastVideoTime(note.getLastVideoTime());
        vo.setCreatedAt(note.getCreatedAt());
        vo.setUpdatedAt(note.getUpdatedAt());
        return vo;
    }
}
