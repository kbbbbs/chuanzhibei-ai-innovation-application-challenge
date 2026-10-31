package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentActionController {
    private final AgentActionExecutionService actionExecutionService;
    private final AgentPendingActionQueryService pendingActionQueryService;

    public AgentActionController(AgentActionExecutionService actionExecutionService,
                                 AgentPendingActionQueryService pendingActionQueryService) {
        this.actionExecutionService = actionExecutionService;
        this.pendingActionQueryService = pendingActionQueryService;
    }

    @GetMapping("/api/agent/actions/pending")
    public Result listPending(@RequestParam(defaultValue = "20") Integer limit) {
        Result forbidden = requireOperationRole();
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(pendingActionQueryService.listCurrentUserPending(limit));
    }

    @GetMapping("/api/agent/actions/pending/{actionId}")
    public Result getPending(@PathVariable String actionId) {
        Result forbidden = requireOperationRole();
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(pendingActionQueryService.getCurrentUserPending(actionId));
    }

    @PostMapping("/api/agent/actions/confirm")
    public Result confirm(@Valid @RequestBody AgentActionConfirmRequest request,
                          HttpServletRequest servletRequest) {
        Result forbidden = requireOperationRole();
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(actionExecutionService.confirm(request, clientIp(servletRequest)));
    }

    private Result requireOperationRole() {
        String roleCode = UserContext.getCurrentRoleCode();
        if (!"teacher".equals(roleCode) && !"admin".equals(roleCode)) {
            return Result.error("403", "学生账号仅开放 Agent 对话权限");
        }
        return null;
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
