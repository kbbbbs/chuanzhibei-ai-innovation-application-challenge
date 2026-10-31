package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.Notice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelatedNoticeServiceTest {
    private NoticeMapper noticeMapper;
    private RelatedNoticeService relatedNoticeService;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(NoticeMapper.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuthUtils.LoginUser teacher = new AuthUtils.LoginUser();
        teacher.setUserId(42);
        teacher.setRoleCode("teacher");
        when(permissionService.currentUser()).thenReturn(teacher);
        when(noticeMapper.insert(any(Notice.class))).thenReturn(1);
        relatedNoticeService = new RelatedNoticeService(noticeMapper, permissionService);
    }

    @Test
    void courseNoticeIsPublishedAsGlobalCourseNotice() {
        Course course = new Course();
        course.setId(7);
        course.setTitle("机器学习导论");
        course.setDescription("从基础概念进入机器学习实践");

        relatedNoticeService.publishCourseNotice(course);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(captor.capture());
        Notice notice = captor.getValue();
        assertEquals(42, notice.getPublisherId());
        assertNull(notice.getClassId());
        assertEquals("course", notice.getNoticeType());
        assertEquals(1, notice.getStatus());
        assertTrue(notice.getTitle().contains("机器学习导论"));
        assertTrue(notice.getContent().contains("课程ID：7"));
    }

    @Test
    void examNoticeTargetsTheTeachingClassAndContainsSchedule() {
        ExamSession exam = new ExamSession();
        exam.setId(9);
        exam.setClassId(12);
        exam.setTitle("机器学习期中考试");
        exam.setStartTime(LocalDateTime.of(2026, 7, 20, 9, 0));
        exam.setEndTime(LocalDateTime.of(2026, 7, 20, 11, 0));
        exam.setDuration(120);

        relatedNoticeService.publishExamNotice(exam);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(captor.capture());
        Notice notice = captor.getValue();
        assertEquals(42, notice.getPublisherId());
        assertEquals(12, notice.getClassId());
        assertEquals("exam", notice.getNoticeType());
        assertTrue(notice.getContent().contains("2026-07-20 09:00"));
        assertTrue(notice.getContent().contains("作答时长：120 分钟"));
    }
}
