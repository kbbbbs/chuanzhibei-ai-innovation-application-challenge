package com.cupk.aiacademy.config;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthInterceptorAsyncTest {
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void asyncReleaseAndAnonymousRequestCannotRetainPreviousUser() {
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "authUtils", mock(AuthUtils.class));
        var user = new AuthUtils.LoginUser(); user.setUserId(2); user.setRoleCode("teacher");
        UserContext.set(user);
        interceptor.afterConcurrentHandlingStarted(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        assertNull(UserContext.get());
        UserContext.set(user);
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/auth/login"),
                new MockHttpServletResponse(), new Object()));
        assertNull(UserContext.get());
    }
}
