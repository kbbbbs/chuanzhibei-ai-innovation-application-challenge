package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.AuthUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentActionExecutionContext(
        AuthUtils.LoginUser user,
        Map<String, Object> parameters,
        String clientIp
) {
    public AgentActionExecutionContext {
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        clientIp = clientIp == null ? "" : clientIp;
    }
}
