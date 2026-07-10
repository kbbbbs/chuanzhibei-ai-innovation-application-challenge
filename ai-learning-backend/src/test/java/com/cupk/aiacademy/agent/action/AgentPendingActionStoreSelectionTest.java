package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.mapper.AgentPendingActionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class AgentPendingActionStoreSelectionTest {
    @Test
    void springSelectsMybatisStoreAsProductionPrimary() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AgentPendingActionMapper.class, () -> mock(AgentPendingActionMapper.class));
            context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
            context.register(InMemoryAgentPendingActionStore.class, MybatisAgentPendingActionStore.class);
            context.refresh();

            assertInstanceOf(MybatisAgentPendingActionStore.class,
                    context.getBean(AgentPendingActionStore.class));
        }
    }
}
