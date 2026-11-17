package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.learning.LearningContentRepository;
import com.cupk.aiacademy.learning.LearningExperienceService;
import com.cupk.aiacademy.mapper.*;
import com.cupk.aiacademy.pojo.*;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseContentAuthorizationTest {
    private final AuthUtils auth = mock(AuthUtils.class);
    private final CourseMapper courses = mock(CourseMapper.class);
    private final ChapterMapper chapters = mock(ChapterMapper.class);
    private final LessonMapper lessons = mock(LessonMapper.class);
    private final PermissionService permissions = new PermissionService(auth, mock(UserMapper.class), courses,
            mock(TeachingClassMapper.class), mock(ClassStudentMapper.class));

    @AfterEach void clearUser() { UserContext.clear(); }

    @Test void removedMembershipStillAllowsOnlyTheStudentsOwnProgressHistory() {
        PermissionService historyPermissions = mock(PermissionService.class);
        LearningProgressMapper progress = mock(LearningProgressMapper.class);
        AuthUtils.LoginUser student = new AuthUtils.LoginUser(); student.setUserId(21); student.setRoleCode("student");
        when(historyPermissions.currentUser()).thenReturn(student);
        when(auth.isStudent(student)).thenReturn(true);
        LearningProgressController controller = new LearningProgressController();
        ReflectionTestUtils.setField(controller, "permissionService", historyPermissions);
        ReflectionTestUtils.setField(controller, "progressMapper", progress);
        ReflectionTestUtils.setField(controller, "authUtils", auth);

        assertEquals("200", controller.pages(12, 999, null, 1, 10).getCode());
        var query = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(progress).selectPage(any(), query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("student_id"));
        assertTrue(sql.contains("class_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(21));
        assertFalse(query.getValue().getParamNameValuePairs().containsValue(999));
        verify(historyPermissions, never()).isStudentInClass(anyInt(), anyInt());
    }

    @Test void courseChapterAndLessonEntryPointsDenyAdministrativeContentAccess() {
        Course course = new Course(); course.setId(10); course.setTeacherId(8);
        when(courses.selectById(10)).thenReturn(course);
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser(); admin.setUserId(1); admin.setRoleCode("admin");
        UserContext.set(admin);
        Chapter chapter = new Chapter(); chapter.setId(11); chapter.setCourseId(10); chapter.setStatus(1);
        when(chapters.selectById(11)).thenReturn(chapter);
        Lesson lesson = new Lesson(); lesson.setId(12); lesson.setChapterId(11);
        when(lessons.selectById(12)).thenReturn(lesson);
        ChapterController chapterController = new ChapterController();
        ReflectionTestUtils.setField(chapterController, "chapterMapper", chapters);
        ReflectionTestUtils.setField(chapterController, "courseMapper", courses);
        ReflectionTestUtils.setField(chapterController, "permissionService", permissions);
        LessonController lessonController = new LessonController();
        ReflectionTestUtils.setField(lessonController, "lessonMapper", lessons);
        ReflectionTestUtils.setField(lessonController, "chapterMapper", chapters);
        ReflectionTestUtils.setField(lessonController, "permissionService", permissions);
        LearningExperienceService experience = new LearningExperienceService(courses, chapters, lessons,
                mock(LearningProgressMapper.class), permissions, mock(LearningContentRepository.class));

        assertThrows(ForbiddenException.class, () -> chapterController.listByCourse(10));
        assertThrows(ForbiddenException.class, () -> chapterController.selectById(11));
        assertThrows(ForbiddenException.class, () -> lessonController.listByChapter(11));
        assertThrows(ForbiddenException.class, () -> lessonController.selectById(12));
        assertThrows(ForbiddenException.class, () -> experience.courseExperience(10, null));
    }

    @Test void aTeacherCannotManageTeachingClassesOrTheirStudentMemberships() {
        AuthUtils.LoginUser teacher = new AuthUtils.LoginUser(); teacher.setUserId(8); teacher.setRoleCode("teacher");
        UserContext.set(teacher);
        TeachingClassController classController = new TeachingClassController();
        ReflectionTestUtils.setField(classController, "permissionService", permissions);
        ClassStudentController members = new ClassStudentController();
        ReflectionTestUtils.setField(members, "permissionService", permissions);

        assertThrows(ForbiddenException.class, () -> classController.add(null));
        assertThrows(ForbiddenException.class, () -> classController.update(1, null));
        assertThrows(ForbiddenException.class, () -> classController.delete(1));
        assertThrows(ForbiddenException.class, () -> members.add(null));
        assertThrows(ForbiddenException.class, () -> members.update(1, null));
        assertThrows(ForbiddenException.class, () -> members.delete(1));
    }
}
