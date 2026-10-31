package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.mapper.*;
import com.cupk.aiacademy.pojo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PermissionServiceRagActorTest {
    private final AuthUtils auth=mock(AuthUtils.class);
    private final UserMapper users=mock(UserMapper.class);
    private final CourseMapper courses=mock(CourseMapper.class);
    private final TeachingClassMapper classes=mock(TeachingClassMapper.class);
    private final ClassStudentMapper members=mock(ClassStudentMapper.class);
    private final PermissionService service=new PermissionService(auth,users,courses,classes,members);

    @ParameterizedTest @ValueSource(strings={"teacher","student","admin"})
    void activeActorsAreResolvedFromCurrentDatabaseState(String role) {
        actor(7,role);
        var resolved=service.resolveActiveUser(7);
        assertEquals(7,resolved.getUserId());assertEquals(role,resolved.getRoleCode());
        assertEquals("user-7",resolved.getUsername());
    }

    @Test void missingOrDisabledAccountsCannotBeResolved() {
        assertThrows(ForbiddenException.class,()->service.resolveActiveUser(null));
        assertThrows(ForbiddenException.class,()->service.resolveActiveUser(-1));
        assertThrows(ForbiddenException.class,()->service.resolveActiveUser(7));
        var user=new User();user.setId(7);user.setStatus(0);when(users.selectById(7)).thenReturn(user);
        assertThrows(ForbiddenException.class,()->service.resolveActiveUser(7));
        user.setStatus(null);assertThrows(ForbiddenException.class,()->service.resolveActiveUser(7));
        verify(auth,never()).findAssignedRoleCode(any());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"guest","administrator","TEACHER"})
    void unassignedOrUnknownRolesAreRejected(String role) {
        actor(7,role);
        assertThrows(ForbiddenException.class,()->service.resolveActiveUser(7));
    }

    @Test void teachersMayUseOnlyTheirOwnCourseWhileAdminDoesNotBypassPrivateMaterialRights() {
        var teacher=actor(7,"teacher");course(10,7,2);
        assertTrue(service.canReadCourseMaterials(teacher,10));
        course(10,8,1);assertFalse(service.canReadCourseMaterials(teacher,10));
        var admin=actor(9,"admin");assertFalse(service.canReadCourseMaterials(admin,10));
    }

    @Test void studentsNeedPublishedCourseAndAnActiveClassMembershipInThatCourse() {
        var student=actor(7,"student");course(10,8,1);
        var membership=new ClassStudent();membership.setClassId(12);membership.setStatus(1);
        when(members.selectList(any())).thenReturn(List.of(membership));
        var classroom=new TeachingClass();classroom.setId(12);classroom.setCourseId(10);classroom.setStatus(1);
        when(classes.selectList(any())).thenReturn(List.of(classroom));
        assertTrue(service.canReadCourseMaterials(student,10));
        classroom.setStatus(0);assertFalse(service.canReadCourseMaterials(student,10));
        classroom.setStatus(1);classroom.setCourseId(11);assertFalse(service.canReadCourseMaterials(student,10));
        classroom.setCourseId(10);course(10,8,2);assertFalse(service.canReadCourseMaterials(student,10));
        course(10,8,1);when(members.selectList(any())).thenReturn(List.of());
        assertFalse(service.canReadCourseMaterials(student,10));
    }

    @Test void missingCourseOrActorDoesNotBecomeAnAuthorizationGrant() {
        var teacher=actor(7,"teacher");
        assertFalse(service.canReadCourseMaterials(teacher,123));
        assertFalse(service.canReadCourseMaterials(null,123));
        assertFalse(service.canReadCourseMaterials(teacher,null));
    }

    private AuthUtils.LoginUser actor(int id,String role) {
        var user=new User();user.setId(id);user.setUsername("user-"+id);user.setStatus(1);
        when(users.selectById(id)).thenReturn(user);when(auth.findAssignedRoleCode(id)).thenReturn(role);
        var actor=new AuthUtils.LoginUser();actor.setUserId(id);actor.setRoleCode(role);return actor;
    }
    private void course(int id,int teacher,int status) {
        var course=new Course();course.setId(id);course.setTeacherId(teacher);course.setStatus(status);
        when(courses.selectById(id)).thenReturn(course);
    }
}
