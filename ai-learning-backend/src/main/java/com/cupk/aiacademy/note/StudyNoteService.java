package com.cupk.aiacademy.note;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.dto.StudyNoteSaveRequest;
import com.cupk.aiacademy.dto.StudyNoteVO;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.StudyNoteMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.StudyNote;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class StudyNoteService {
    private final StudyNoteMapper studyNoteMapper;
    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final PermissionService permissionService;

    public StudyNoteService(StudyNoteMapper studyNoteMapper,
                            LessonMapper lessonMapper,
                            ChapterMapper chapterMapper,
                            CourseMapper courseMapper,
                            PermissionService permissionService) {
        this.studyNoteMapper = studyNoteMapper;
        this.lessonMapper = lessonMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public StudyNoteVO getMyNote(Integer lessonId) {
        Lesson lesson = getLesson(lessonId);
        Course course = getCourseByLesson(lesson);
        requireLessonAccess(course);

        AuthUtils.LoginUser user = permissionService.currentUser();
        StudyNote note = findNote(user.getUserId(), lesson.getId());
        if (note == null) {
            note = buildEmptyNote(user.getUserId(), course.getId(), lesson.getId(), lesson.getTitle());
        }
        return StudyNoteVO.from(note);
    }

    @Transactional
    public StudyNoteVO saveMyNote(Integer lessonId, StudyNoteSaveRequest request) {
        Lesson lesson = getLesson(lessonId);
        Course course = getCourseByLesson(lesson);
        requireLessonAccess(course);

        AuthUtils.LoginUser user = permissionService.currentUser();
        StudyNote note = findNote(user.getUserId(), lesson.getId());
        LocalDateTime now = LocalDateTime.now();
        if (note == null) {
            note = new StudyNote();
            note.setUserId(user.getUserId());
            note.setCourseId(course.getId());
            note.setLessonId(lesson.getId());
            note.setCreatedAt(now);
        }

        note.setTitle(fixTitle(request.getTitle(), lesson.getTitle()));
        note.setContentMd(request.getContentMd() == null ? "" : request.getContentMd());
        note.setLastVideoTime(request.getLastVideoTime() == null ? 0 : request.getLastVideoTime());
        note.setUpdatedAt(now);

        if (note.getId() == null) {
            studyNoteMapper.insert(note);
        } else {
            studyNoteMapper.updateById(note);
        }
        return StudyNoteVO.from(note);
    }

    @Transactional(readOnly = true)
    public String getDownloadContent(Integer lessonId) {
        StudyNoteVO note = getMyNote(lessonId);
        return note.getContentMd() == null ? "" : note.getContentMd();
    }

    @Transactional(readOnly = true)
    public String getDownloadFileName(Integer lessonId) {
        StudyNoteVO note = getMyNote(lessonId);
        String title = note.getTitle() == null || note.getTitle().isBlank() ? "study-note" : note.getTitle().trim();
        return title.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_") + ".md";
    }

    private StudyNote findNote(Integer userId, Integer lessonId) {
        return studyNoteMapper.selectOne(new QueryWrapper<StudyNote>()
                .eq("user_id", userId)
                .eq("lesson_id", lessonId)
                .last("LIMIT 1"));
    }

    private StudyNote buildEmptyNote(Integer userId, Integer courseId, Integer lessonId, String lessonTitle) {
        StudyNote note = new StudyNote();
        note.setUserId(userId);
        note.setCourseId(courseId);
        note.setLessonId(lessonId);
        note.setTitle(fixTitle("", lessonTitle));
        note.setContentMd("");
        note.setLastVideoTime(0);
        return note;
    }

    private Lesson getLesson(Integer lessonId) {
        if (lessonId == null || lessonId <= 0) {
            throw new BusinessException("课时不能为空");
        }
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        return lesson;
    }

    private Course getCourseByLesson(Lesson lesson) {
        Chapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private void requireLessonAccess(Course course) {
        permissionService.requireCourseContentRead(course.getId());
    }

    private String fixTitle(String title, String lessonTitle) {
        String value = title == null || title.isBlank() ? lessonTitle : title.trim();
        if (value == null || value.isBlank()) {
            return "学习笔记";
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}
