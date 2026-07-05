package com.cupk.aiacademy.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedApiExceptionUsesApiResponseShape() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/error");

        ResponseEntity<Object> response = handler.handleException(new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiResponse<?> body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertFalse(body.success());
        assertEquals("boom", body.message());
    }

    @Test
    void unexpectedMainlineExceptionUsesResultShape() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/courses");

        ResponseEntity<Object> response = handler.handleException(new RuntimeException(), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Result body = assertInstanceOf(Result.class, response.getBody());
        assertEquals("500", body.getCode());
        assertEquals("服务器内部错误", body.getMsg());
    }

    @Test
    void missingRouteReturnsNotFoundResult() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/questions");

        ResponseEntity<Object> response = handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/questions"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Result body = assertInstanceOf(Result.class, response.getBody());
        assertEquals("404", body.getCode());
    }
}
