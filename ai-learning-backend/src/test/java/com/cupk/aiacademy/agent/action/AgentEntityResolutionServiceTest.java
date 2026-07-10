package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEntityResolutionServiceTest {
    @Mock private CourseCategoryMapper courseCategoryMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private LessonMapper lessonMapper;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private HomeworkMapper homeworkMapper;
    @Mock private NoticeMapper noticeMapper;
    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamPaperQuestionMapper examPaperQuestionMapper;
    @Mock private ExamSessionMapper examSessionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private UserMapper userMapper;

    @Test
    void resolveCourseTitleToCourseId() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1, "Java 入门")));
        AgentEntityResolutionService service = service();

        Map<String, Object> resolved = service.resolve("chapter.create", Map.of(
                "courseTitle", "Java 入门",
                "title", "第一章"
        ));

        assertEquals(1, resolved.get("courseId"));
        assertEquals("Java 入门", resolved.get("courseTitle"));
    }

    @Test
    void resolveCourseTitleRejectsAmbiguousMatches() {
        when(courseMapper.selectList(any())).thenReturn(List.of(
                course(1, "Java 入门"),
                course(2, "Java 入门")
        ));
        AgentEntityResolutionService service = service();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.resolve(
                "chapter.create",
                Map.of("courseTitle", "Java 入门")
        ));

        assertEquals("课程匹配到多条记录：[1(Java 入门), 2(Java 入门)]，请补充ID", exception.getMessage());
    }

    @Test
    void homeworkCreateCanResolveSingleTeachingClassFromCourseTitle() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1, "Java 入门")));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass(11, 1, "Java 入门一班")));
        AgentEntityResolutionService service = service();

        Map<String, Object> resolved = service.resolve("homework.create", Map.of(
                "courseTitle", "Java 入门",
                "title", "第一次练习"
        ));

        assertEquals(1, resolved.get("courseId"));
        assertEquals(11, resolved.get("classId"));
        assertEquals("Java 入门一班", resolved.get("className"));
    }

    @Test
    void homeworkCreateRequiresClassWhenCourseHasMultipleTeachingClasses() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1, "Java 入门")));
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(
                teachingClass(11, 1, "Java 入门一班"),
                teachingClass(12, 1, "Java 入门二班")
        ));
        AgentEntityResolutionService service = service();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.resolve(
                "homework.create",
                Map.of("courseTitle", "Java 入门")
        ));

        assertEquals("课程下匹配到多个教学班：[11(Java 入门一班), 12(Java 入门二班)]，请补充 classId 或 className",
                exception.getMessage());
    }

    private AgentEntityResolutionService service() {
        return new AgentEntityResolutionService(
                courseCategoryMapper,
                courseMapper,
                chapterMapper,
                lessonMapper,
                teachingClassMapper,
                classStudentMapper,
                homeworkMapper,
                noticeMapper,
                examPaperMapper,
                examPaperQuestionMapper,
                examSessionMapper,
                questionMapper,
                userMapper
        );
    }

    private Course course(Integer id, String title) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        return course;
    }

    private TeachingClass teachingClass(Integer id, Integer courseId, String className) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(courseId);
        teachingClass.setClassName(className);
        return teachingClass;
    }
}
