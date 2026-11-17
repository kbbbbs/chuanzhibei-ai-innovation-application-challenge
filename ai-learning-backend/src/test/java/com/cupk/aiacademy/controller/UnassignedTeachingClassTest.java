package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.dto.TeachingClassDto;
import com.cupk.aiacademy.dto.ClassStudentDto;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnassignedTeachingClassTest {
    @Test void administratorCanEnrollAStudentWhileTheClassIsUnassigned() {
        var classes=mock(TeachingClassMapper.class);
        var members=mock(ClassStudentMapper.class);
        var jdbc=mock(JdbcTemplate.class);
        var permissions=mock(PermissionService.class);
        var waiting=new TeachingClass(); waiting.setId(13); waiting.setTeacherId(7);
        waiting.setClassName("人工智能一班"); waiting.setStatus(1);
        when(permissions.getTeachingClass(13)).thenReturn(waiting);
        when(classes.selectById(13)).thenReturn(waiting);
        when(classes.selectForUpdate(13)).thenReturn(waiting);
        when(members.insert(any(ClassStudent.class))).thenReturn(1);
        var controller=new ClassStudentController();
        ReflectionTestUtils.setField(controller,"classMapper",classes);
        ReflectionTestUtils.setField(controller,"classStudentMapper",members);
        ReflectionTestUtils.setField(controller,"jdbc",jdbc);
        ReflectionTestUtils.setField(controller,"permissionService",permissions);
        var request=new ClassStudentDto(); request.setClassId(13); request.setStudentId(24); request.setStatus(1);
        assertEquals("200",controller.add(request).getCode());
        verify(members).insert(any(ClassStudent.class));
        verifyNoInteractions(jdbc);
    }

    @Test void administratorCanCreateWaitingClassBeforeAnyCourseExists() {
        var classes=mock(TeachingClassMapper.class);
        var jdbc=mock(JdbcTemplate.class);
        var permissions=mock(PermissionService.class);
        when(classes.insert(any(TeachingClass.class))).thenReturn(1);
        var controller=controller(classes,jdbc,permissions);
        var request=new TeachingClassDto();
        request.setTeacherId(7); request.setClassName("人工智能一班"); request.setStatus(1);
        try (var factory=Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
        assertEquals("200",controller.add(request).getCode());
        var inserted=org.mockito.ArgumentCaptor.forClass(TeachingClass.class);
        verify(classes).insert(inserted.capture());
        assertNull(inserted.getValue().getCourseId());
        assertFalse(inserted.getValue().getCourseOpen());
        verify(permissions).requireAdmin();
        verifyNoInteractions(jdbc);
    }

    @Test void administratorCanUnassignAnUnusedClassAndRetainItsIdentity() {
        var classes=mock(TeachingClassMapper.class);
        var jdbc=mock(JdbcTemplate.class);
        var existing=new TeachingClass(); existing.setId(13); existing.setCourseId(1); existing.setTeacherId(7);
        existing.setClassName("人工智能一班"); existing.setStatus(1); existing.setCourseOpen(false);
        when(classes.selectById(13)).thenReturn(existing);
        when(classes.selectForUpdate(13)).thenReturn(existing);
        when(classes.updateById(any(TeachingClass.class))).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT COUNT"),eq(Long.class),eq(13))).thenReturn(0L);
        var request=TeachingClassDto.fromEntity(existing); request.setCourseId(null);
        assertEquals("200",controller(classes,jdbc,mock(PermissionService.class)).update(13,request).getCode());
        var updated=org.mockito.ArgumentCaptor.forClass(TeachingClass.class);
        verify(classes).updateById(updated.capture());
        assertEquals(13,updated.getValue().getId());
        assertNull(updated.getValue().getCourseId());
        assertFalse(updated.getValue().getCourseOpen());
        verify(jdbc).update(contains("workflow_version"),eq(1));
    }

    private TeachingClassController controller(TeachingClassMapper classes,JdbcTemplate jdbc,PermissionService permissions) {
        var controller=new TeachingClassController();
        ReflectionTestUtils.setField(controller,"classMapper",classes);
        ReflectionTestUtils.setField(controller,"jdbc",jdbc);
        ReflectionTestUtils.setField(controller,"permissionService",permissions);
        ReflectionTestUtils.setField(controller,"authUtils",mock(AuthUtils.class));
        return controller;
    }
}
