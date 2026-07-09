package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.pojo.OperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionExecutionPreValidationTest {
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void confirmValidatesAndNormalizesParametersBeforeExecutor() {
        loginAs(7);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        CapturingExecutor executor = new CapturingExecutor();
        AgentActionExecutionService service = service(executor, operationLogMapper);

        AgentActionExecutionResult result = service.confirm(new AgentActionConfirmRequest(
                "sample.create",
                true,
                Map.of("rawRequest", "创建样例", "title", "样例", "count", "2")
        ), "127.0.0.1");

        assertEquals("sample.create", result.actionCode());
        assertEquals(2, executor.parameters.get("count"));
        assertEquals(false, executor.parameters.containsKey("rawRequest"));
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    void confirmRejectsUnknownFieldBeforeExecutor() {
        loginAs(7);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        CapturingExecutor executor = new CapturingExecutor();
        AgentActionExecutionService service = service(executor, operationLogMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                new AgentActionConfirmRequest("sample.create", true, Map.of(
                        "title", "样例",
                        "count", 2,
                        "unexpected", "x"
                )),
                ""
        ));

        assertEquals("参数校验失败，问题：unexpected 字段不在动作 schema 中", exception.getMessage());
        assertEquals(null, executor.parameters);
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    private AgentActionExecutionService service(CapturingExecutor executor, OperationLogMapper operationLogMapper) {
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(sampleTool()));
        AgentActionPreExecutionValidator preValidator = new AgentActionPreExecutionValidator(
                catalog,
                new AgentActionSchemaValidator(),
                null
        );
        return new AgentActionExecutionService(
                List.of(executor),
                operationLogMapper,
                new ObjectMapper(),
                new InMemoryAgentPendingActionStore(),
                preValidator
        );
    }

    private AgentTool sampleTool() {
        AgentActionSpec spec = new AgentActionSpec(
                "sample.create",
                "创建样例",
                "sample",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "创建样例",
                List.of(
                        new AgentActionParameterSpec("title", "标题", true),
                        new AgentActionParameterSpec("count", "数量", true)
                ),
                List.of("会创建样例"),
                Duration.ofMinutes(10)
        );
        return new AgentTool() {
            @Override
            public String name() {
                return "sample_create_plan";
            }

            @Override
            public Optional<AgentActionSpec> actionSpec() {
                return Optional.of(spec);
            }

            @Override
            public AgentToolResult execute(AgentToolContext context) {
                return null;
            }
        };
    }

    private void loginAs(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode("teacher");
        UserContext.set(user);
    }

    private static class CapturingExecutor implements AgentActionExecutor {
        private Map<String, Object> parameters;

        @Override
        public String actionCode() {
            return "sample.create";
        }

        @Override
        public String actionName() {
            return "创建样例";
        }

        @Override
        public AgentActionRiskLevel riskLevel() {
            return AgentActionRiskLevel.MEDIUM;
        }

        @Override
        public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
            parameters = context.parameters();
            return new AgentActionExecutionResult(
                    "sample.create",
                    "创建样例",
                    true,
                    "ok",
                    Map.of("parameters", parameters),
                    null
            );
        }
    }
}
