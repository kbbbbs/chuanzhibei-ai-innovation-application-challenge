package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.config.AuthInterceptor;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.service.CourseResourceService;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryAuthorizationTest {
    private CourseCategoryMapper categories;
    private CourseMapper courses;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        categories = mock(CourseCategoryMapper.class);
        courses = mock(CourseMapper.class);
        AuthUtils auth = spy(new AuthUtils());
        for (String role : List.of("student", "teacher", "admin")) {
            AuthUtils.LoginUser actor = new AuthUtils.LoginUser();
            actor.setUserId(1);
            actor.setRoleCode(role);
            doReturn(actor).when(auth).requireLogin("Bearer " + role);
        }
        PermissionService permissions = new PermissionService(auth, mock(UserMapper.class), courses,
                mock(TeachingClassMapper.class), mock(ClassStudentMapper.class));
        CategoryController controller = new CategoryController();
        ReflectionTestUtils.setField(controller, "categoryMapper", categories);
        ReflectionTestUtils.setField(controller, "courseMapper", courses);
        ReflectionTestUtils.setField(controller, "courseResourceService", mock(CourseResourceService.class));
        ReflectionTestUtils.setField(controller, "permissionService", permissions);
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "authUtils", auth);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor).build();
    }

    @Test
    void nonAdminsCannotWriteAndAnonymousMustAuthenticate() throws Exception {
        for (String role : List.of("student", "teacher")) {
            for (MockHttpServletRequestBuilder request : writes()) {
                mvc.perform(request.header("Authorization", "Bearer " + role))
                        .andExpect(status().isForbidden());
            }
        }
        for (MockHttpServletRequestBuilder request : writes()) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(categories, courses);
    }

    @Test
    void adminCanWriteIncludingCascade() throws Exception {
        when(categories.insert(any(CourseCategory.class))).thenReturn(1);
        when(categories.selectById(7)).thenReturn(new CourseCategory());
        when(categories.updateById(any(CourseCategory.class))).thenReturn(1);
        when(categories.deleteById(7)).thenReturn(1);
        when(courses.selectCount(any())).thenReturn(0L, 1L);
        when(courses.delete(any())).thenReturn(1);
        List<MockHttpServletRequestBuilder> requests = writes();
        for (int i = 0; i < requests.size(); i++) {
            var result = mvc.perform(requests.get(i).header("Authorization", "Bearer admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"));
            if (i == 3) {
                result.andExpect(jsonPath("$.data.deletedCourses").value(1));
            }
        }
        verify(categories).insert(any(CourseCategory.class));
        verify(categories).updateById(any(CourseCategory.class));
        verify(categories, times(2)).deleteById(7);
        verify(courses).delete(any());
    }

    @Test
    void authenticatedUsersCanRead() throws Exception {
        when(categories.selectList(any())).thenReturn(List.of());
        for (String role : List.of("student", "teacher", "admin")) {
            mvc.perform(get("/categories").header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk());
        }
        verify(categories, times(3)).selectList(any());
    }

    private List<MockHttpServletRequestBuilder> writes() {
        String body = "{\"name\":\"Test category\"}";
        return List.of(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body),
                put("/categories/7").contentType(MediaType.APPLICATION_JSON).content(body),
                delete("/categories/7"), delete("/categories/7").param("cascade", "true"));
    }
}
