package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.NoticeDto;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeControllerTest {

    @Test
    void createNoticeUsesCurrentUserAsPublisher() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        PermissionService permissionService = mock(PermissionService.class);

        AuthUtils.LoginUser currentUser = new AuthUtils.LoginUser();
        currentUser.setUserId(42);
        currentUser.setRoleCode("admin");
        when(permissionService.currentUser()).thenReturn(currentUser);
        when(userMapper.selectById(42)).thenReturn(new User());
        when(noticeMapper.insert(any(Notice.class))).thenReturn(1);

        NoticeController controller = new NoticeController();
        ReflectionTestUtils.setField(controller, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "classMapper", mock(TeachingClassMapper.class));
        ReflectionTestUtils.setField(controller, "permissionService", permissionService);
        ReflectionTestUtils.setField(controller, "authUtils", mock(AuthUtils.class));

        NoticeDto request = new NoticeDto();
        request.setTitle("课程调整通知");
        request.setContent("本周课程时间有调整");
        request.setNoticeType("course");
        request.setStatus(1);

        Result result = controller.add(request);

        assertEquals("200", result.getCode());
        ArgumentCaptor<Notice> noticeCaptor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(noticeCaptor.capture());
        assertEquals(42, noticeCaptor.getValue().getPublisherId());
        assertEquals("课程调整通知", noticeCaptor.getValue().getTitle());
    }
}
