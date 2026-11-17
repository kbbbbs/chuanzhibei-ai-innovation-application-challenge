package com.cupk.aiacademy.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    private MockHttpServletResponse preflight(String allowedOrigins, String origin) throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/auth/login");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        var response = new MockHttpServletResponse();
        new CorsConfig().corsFilter(allowedOrigins).doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void sameOriginOnlyByDefault() throws Exception {
        assertNull(preflight("", "https://attacker.example").getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void configuredOriginsAreAllowedExactly() throws Exception {
        String allowed = " http://localhost:5173 , https://learning.example.edu ";
        assertEquals("https://learning.example.edu",
                preflight(allowed, "https://learning.example.edu").getHeader("Access-Control-Allow-Origin"));
        var rejected = preflight(allowed, "https://attacker.example");
        assertNull(rejected.getHeader("Access-Control-Allow-Origin"));
        assertEquals(403, rejected.getStatus());
    }
}
