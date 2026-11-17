package com.cupk.aiacademy.installation;

import com.cupk.installation.InstallationLifecycle;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** A persisted apply operation is started only after its 202 response is flushed. */
public final class InstallationDrainFilter extends OncePerRequestFilter {
    private final InstallationLifecycle lifecycle;
    private final ConfigurableApplicationContext context;
    public InstallationDrainFilter(InstallationLifecycle lifecycle,ConfigurableApplicationContext context) {
        this.lifecycle=lifecycle;this.context=context;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try { chain.doFilter(request,response); }
        finally {
            if(Boolean.TRUE.equals(request.getAttribute("installation.beginDrain"))&&response.getStatus()==202) {
                response.flushBuffer();
                lifecycle.beginDrain(context);
            }
        }
    }
}
