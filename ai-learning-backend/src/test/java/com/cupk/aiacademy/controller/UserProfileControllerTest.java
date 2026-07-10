package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.PasswordUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.UserProfileDeleteRequest;
import com.cupk.aiacademy.dto.UserProfileUpdateRequest;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {
    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private UserController controller;

    private User user;

    @BeforeEach
    void setUp() {
        AuthUtils.LoginUser loginUser = new AuthUtils.LoginUser();
        loginUser.setUserId(7);
        loginUser.setUsername("profile-user");
        loginUser.setRoleCode("student");

        user = new User();
        user.setId(7);
        user.setUsername("profile-user");
        user.setNickname("Before");
        user.setPassword(PasswordUtils.encode("old-password"));
        user.setStatus(1);

        when(permissionService.currentUser()).thenReturn(loginUser);
        when(userMapper.selectById(7)).thenReturn(user);
        lenient().when(userRoleMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void readsCurrentUserProfile() {
        Result result = controller.currentProfile();

        assertEquals("200", result.getCode());
        assertEquals(user, result.getData());
        verify(userMapper).selectById(7);
    }

    @Test
    void updatesOwnProfileAndPassword() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("After");
        request.setEmail("after@example.com");
        request.setPhone("13800138000");
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");

        Result result = controller.updateCurrentProfile(request);

        assertEquals("200", result.getCode());
        assertEquals("After", user.getNickname());
        assertEquals("after@example.com", user.getEmail());
        assertTrue(PasswordUtils.matches("new-password", user.getPassword()));
        verify(userMapper).updateById(user);
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordIsWrong() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("After");
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password");

        Result result = controller.updateCurrentProfile(request);

        assertEquals("400", result.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void deactivatesOwnAccountAfterPasswordConfirmation() {
        UserProfileDeleteRequest request = new UserProfileDeleteRequest();
        request.setCurrentPassword("old-password");

        Result result = controller.deactivateCurrentAccount(request);

        assertEquals("200", result.getCode());
        assertEquals(0, user.getStatus());
        verify(userMapper).updateById(user);
    }

    @Test
    void keepsLastAdministratorActive() {
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser();
        admin.setUserId(7);
        admin.setUsername("profile-user");
        admin.setRoleCode("admin");
        when(permissionService.currentUser()).thenReturn(admin);

        Role adminRole = new Role();
        adminRole.setId(1);
        adminRole.setRoleCode("admin");
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        when(userMapper.selectCount(any())).thenReturn(1L);

        UserProfileDeleteRequest request = new UserProfileDeleteRequest();
        request.setCurrentPassword("old-password");
        Result result = controller.deactivateCurrentAccount(request);

        assertEquals("409", result.getCode());
        assertEquals(1, user.getStatus());
        verify(userMapper, never()).updateById(any(User.class));
    }
}
