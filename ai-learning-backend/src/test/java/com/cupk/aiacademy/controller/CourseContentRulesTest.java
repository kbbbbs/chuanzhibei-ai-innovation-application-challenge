package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cupk.aiacademy.dto.ChapterDto;
import com.cupk.aiacademy.dto.LessonDto;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Content rules the course workspace relies on: lesson types, content, media binding and ordering. */
class CourseContentRulesTest {
    private final LessonMapper lessons = mock(LessonMapper.class);
    private final ChapterMapper chapters = mock(ChapterMapper.class);
    private final CourseMapper courses = mock(CourseMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final LessonController lessonController = new LessonController();
    private final ChapterController chapterController = new ChapterController();

    @BeforeEach
    void setup() {
        Chapter chapter = new Chapter();
        chapter.setId(11);
        chapter.setCourseId(10);
        chapter.setStatus(1);
        when(chapters.selectById(11)).thenReturn(chapter);
        when(courses.selectById(10)).thenReturn(new Course());
        when(lessons.insert(any(Lesson.class))).thenReturn(1);
        when(lessons.updateById(any(Lesson.class))).thenReturn(1);
        when(chapters.insert(any(Chapter.class))).thenReturn(1);
        ReflectionTestUtils.setField(lessonController, "lessonMapper", lessons);
        ReflectionTestUtils.setField(lessonController, "chapterMapper", chapters);
        ReflectionTestUtils.setField(lessonController, "permissionService", permissions);
        ReflectionTestUtils.setField(lessonController, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(chapterController, "chapterMapper", chapters);
        ReflectionTestUtils.setField(chapterController, "courseMapper", courses);
        ReflectionTestUtils.setField(chapterController, "permissionService", permissions);
    }

    private LessonDto lesson(String type, String content) {
        LessonDto dto = new LessonDto();
        dto.setChapterId(11);
        dto.setTitle("课时");
        dto.setResourceType(type);
        dto.setContent(content);
        return dto;
    }

    @Test
    void unknownResourceTypesAndEmptyTextLessonsAreRejected() {
        assertEquals("400", lessonController.add(lesson("hologram", "")).getCode());
        assertEquals("400", lessonController.add(lesson("markdown", "  ")).getCode());
        assertEquals("400", lessonController.add(lesson("article", null)).getCode());
        assertEquals("200", lessonController.add(lesson("markdown", "正文")).getCode());
        assertEquals("200", lessonController.add(lesson("pdf", "")).getCode());
        verify(lessons, times(2)).insert(any(Lesson.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void changingAVideoLessonToTextClearsItsVideoBinding() {
        Lesson video = new Lesson();
        video.setId(5);
        video.setChapterId(11);
        video.setResourceType("video_url");
        video.setVideoResourceId(3L);
        when(lessons.selectById(5)).thenReturn(video);

        assertEquals("200", lessonController.update(5, lesson("markdown", "正文")).getCode());

        ArgumentCaptor<UpdateWrapper> cleared = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(lessons).update(isNull(), cleared.capture());
        String sql = cleared.getValue().getSqlSet();
        assertTrue(sql.contains("video_resource_id"), sql);
        assertTrue(cleared.getValue().getParamNameValuePairs().containsValue(null));
    }

    @Test
    void keepingTheVideoTypeLeavesTheBindingAlone() {
        Lesson video = new Lesson();
        video.setId(5);
        video.setChapterId(11);
        video.setResourceType("video_url");
        video.setVideoResourceId(3L);
        when(lessons.selectById(5)).thenReturn(video);
        LessonDto dto = lesson("video_url", "");
        dto.setResourceUrl("https://example.com/a.mp4");

        assertEquals("200", lessonController.update(5, dto).getCode());
        verify(lessons, never()).update(isNull(), any());
    }

    @Test
    void newLessonsAndChaptersWithoutAPositionGoLast() {
        Lesson first = new Lesson();
        first.setSortOrder(4);
        when(lessons.selectList(any())).thenReturn(List.of(first));
        lessonController.add(lesson("markdown", "正文"));
        ArgumentCaptor<Lesson> inserted = ArgumentCaptor.forClass(Lesson.class);
        verify(lessons).insert(inserted.capture());
        assertEquals(5, inserted.getValue().getSortOrder());

        Chapter existing = new Chapter();
        existing.setSortOrder(2);
        when(chapters.selectList(any())).thenReturn(List.of(existing));
        ChapterDto chapter = new ChapterDto();
        chapter.setCourseId(10);
        chapter.setTitle("新章节");
        chapterController.add(chapter);
        ArgumentCaptor<Chapter> created = ArgumentCaptor.forClass(Chapter.class);
        verify(chapters).insert(created.capture());
        assertEquals(3, created.getValue().getSortOrder());
    }
}
