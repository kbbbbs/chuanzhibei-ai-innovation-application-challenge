package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.mapper.*;
import com.cupk.aiacademy.pojo.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoursePublicationAccessTest {
    private final AuthUtils auth = mock(AuthUtils.class);
    private final UserMapper users = mock(UserMapper.class);
    private final CourseMapper courses = mock(CourseMapper.class);
    private final TeachingClassMapper classes = mock(TeachingClassMapper.class);
    private final ClassStudentMapper members = mock(ClassStudentMapper.class);
    private final PermissionService permissions = new PermissionService(auth, users, courses, classes, members);

    @Test
    void aPublishedCourseDoesNotOpenAnUnselectedClass() {
        User user = new User(); user.setId(7); user.setStatus(1);
        when(users.selectById(7)).thenReturn(user);
        when(auth.findAssignedRoleCode(7)).thenReturn("student");
        AuthUtils.LoginUser student = new AuthUtils.LoginUser(); student.setUserId(7); student.setRoleCode("student");
        Course course = new Course(); course.setId(10); course.setTeacherId(8); course.setStatus(1);
        when(courses.selectById(10)).thenReturn(course);
        ClassStudent membership = new ClassStudent(); membership.setClassId(12); membership.setStatus(1);
        when(members.selectList(any())).thenReturn(List.of(membership));
        TeachingClass classroom = new TeachingClass(); classroom.setId(12); classroom.setCourseId(10); classroom.setStatus(1);
        when(classes.selectList(any())).thenReturn(List.of(classroom));

        assertFalse(permissions.canReadCourseMaterials(student, 10));
        assertEquals(List.of(), permissions.studentCourseIds(7));
        classroom.setCourseOpen(false);
        assertFalse(permissions.canReadCourseMaterials(student, 10));
        classroom.setCourseOpen(true);
        assertTrue(permissions.canReadCourseMaterials(student, 10));
        assertEquals(List.of(12), permissions.studentLearningClassIds(7));
        classroom.setStatus(0);
        assertFalse(permissions.canReadCourseMaterials(student, 10));
        classroom.setStatus(1); course.setStatus(2);
        assertFalse(permissions.canReadCourseMaterials(student, 10));
    }

    @Test
    void administratorsAndOtherTeachersCannotReadOrWriteCourseContent() {
        Course course = new Course(); course.setId(10); course.setTeacherId(8);
        when(courses.selectById(10)).thenReturn(course);
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser(); admin.setUserId(1); admin.setRoleCode("admin");
        UserContext.set(admin);
        try {
            assertThrows(ForbiddenException.class, () -> permissions.requireCourseContentRead(10));
            assertThrows(ForbiddenException.class, () -> permissions.requireCourseContentTeacher(10));
            AuthUtils.LoginUser teacher = new AuthUtils.LoginUser(); teacher.setUserId(7); teacher.setRoleCode("teacher");
            when(auth.isTeacher(teacher)).thenReturn(true); UserContext.set(teacher);
            assertThrows(ForbiddenException.class, () -> permissions.requireCourseContentRead(10));
            course.setTeacherId(7);
            assertDoesNotThrow(() -> permissions.requireCourseContentRead(10));
            assertDoesNotThrow(() -> permissions.requireCourseContentTeacher(10));
        } finally { UserContext.clear(); }
    }

    @Test
    void learningProgressRequiresTheSpecificOpenClassEvenWhenAnotherClassIsOpen() {
        Course course = new Course(); course.setId(10); course.setStatus(1);
        TeachingClass classroom = new TeachingClass(); classroom.setId(12); classroom.setCourseId(10); classroom.setStatus(1); classroom.setCourseOpen(false);
        when(courses.selectById(10)).thenReturn(course);
        when(classes.selectById(12)).thenReturn(classroom);
        assertThrows(ForbiddenException.class, () -> permissions.requireStudentLearningClass(12, 10, 7));
        classroom.setCourseOpen(true);
        when(members.selectCount(any())).thenReturn(1L);
        assertDoesNotThrow(() -> permissions.requireStudentLearningClass(12, 10, 7));
        when(members.selectCount(any())).thenReturn(0L);
        assertThrows(ForbiddenException.class, () -> permissions.requireStudentLearningClass(12, 10, 7));
    }
}
