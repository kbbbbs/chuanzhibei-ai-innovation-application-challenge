package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentActionController {
    private final AgentActionExecutionService actionExecutionService;

    public AgentActionController(AgentActionExecutionService actionExecutionService) {
        this.actionExecutionService = actionExecutionService;
    }

    @PostMapping("/api/agent/actions/confirm")
    public Result confirm(@Valid @RequestBody AgentActionConfirmRequest request,
                          HttpServletRequest servletRequest) {
        return Result.success(actionExecutionService.confirm(request, clientIp(servletRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
