package com.cupk.aiacademy.common;

import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthUtilsAssignedRoleTest {
    private final UserRoleMapper assignments=mock(UserRoleMapper.class);
    private final RoleMapper roles=mock(RoleMapper.class);
    private final AuthUtils auth=new AuthUtils();

    AuthUtilsAssignedRoleTest() {
        ReflectionTestUtils.setField(auth,"userRoleMapper",assignments);
        ReflectionTestUtils.setField(auth,"roleMapper",roles);
    }

    @Test void explicitResolutionDoesNotInventAStudentRoleForMissingAssignments() {
        assertNull(auth.findAssignedRoleCode(null));
        verifyNoInteractions(assignments,roles);
        assertNull(auth.findAssignedRoleCode(5));
        assertEquals("student",auth.findRoleCode(5),"Existing legacy role default remains compatible");
    }

    @Test void returnsOnlyAnActuallyAssignedRole() {
        var assignment=new UserRole();assignment.setRoleId(2);
        when(assignments.selectOne(any())).thenReturn(assignment);
        assertNull(auth.findAssignedRoleCode(5));
        var role=new Role();role.setRoleCode("teacher");when(roles.selectById(2)).thenReturn(role);
        assertEquals("teacher",auth.findAssignedRoleCode(5));
        assertEquals("teacher",auth.findRoleCode(5));
    }
}
