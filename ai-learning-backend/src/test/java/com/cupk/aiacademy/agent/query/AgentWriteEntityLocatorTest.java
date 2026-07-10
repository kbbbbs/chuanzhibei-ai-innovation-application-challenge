package com.cupk.aiacademy.agent.query;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Notice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWriteEntityLocatorTest {
    @Mock private CourseMapper courseMapper;
    @Mock private NoticeMapper noticeMapper;
    @Mock private TeachingClassMapper teachingClassMapper;

    private AgentWriteEntityLocator locator() {
        return new AgentWriteEntityLocator(courseMapper, noticeMapper, teachingClassMapper);
    }

    @Test
    void locateCourseByIdReturnsCourse() {
        Course course = course(11, "Java 入门");
        when(courseMapper.selectById(11)).thenReturn(course);
        Course result = locator().locateCourse(11, null, null, null, null);
        assertEquals(11, result.getId());
        assertEquals("Java 入门", result.getTitle());
    }

    @Test
    void locateCourseByTitleFuzzyMatchReturnsUnique() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(11, "Java 入门基础")));
        Course result = locator().locateCourse(null, "Java 入门", null, null, null);
        assertEquals(11, result.getId());
    }

    @Test
    void locateCourseMultipleMatchesThrowsWithCandidates() {
        when(courseMapper.selectList(any())).thenReturn(List.of(
                course(11, "Java 入门"), course(12, "Java 进阶")));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> locator().locateCourse(null, "Java", null, null, null));
        assertTrue(ex.getMessage().contains("匹配到多门课程"));
        assertTrue(ex.getMessage().contains("11=Java 入门"));
        assertTrue(ex.getMessage().contains("12=Java 进阶"));
    }

    @Test
    void locateCourseNoLocatorThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> locator().locateCourse(null, null, null, null, null));
        assertTrue(ex.getMessage().contains("唯一定位条件"));
    }

    @Test
    void locateCourseNotFoundThrows() {
        when(courseMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> locator().locateCourse(null, "不存在", null, null, null));
        assertTrue(ex.getMessage().contains("未找到匹配课程"));
    }

    @Test
    void locateNoticeByTitleFuzzyMatchReturnsUnique() {
        Notice notice = new Notice();
        notice.setId(201);
        notice.setTitle("考试安排");
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice));
        Notice result = locator().locateNotice(null, "考试");
        assertEquals(201, result.getId());
    }

    @Test
    void locateNoticeMultipleMatchesThrowsWithCandidates() {
        Notice a = new Notice();
        a.setId(201);
        a.setTitle("考试安排");
        Notice b = new Notice();
        b.setId(202);
        b.setTitle("考试复习");
        when(noticeMapper.selectList(any())).thenReturn(List.of(a, b));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> locator().locateNotice(null, "考试"));
        assertTrue(ex.getMessage().contains("匹配到多条通知"));
        assertTrue(ex.getMessage().contains("201=考试安排"));
    }

    private Course course(Integer id, String title) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        return course;
    }
}