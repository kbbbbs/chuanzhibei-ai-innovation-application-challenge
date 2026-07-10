package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.mapper.AgentPendingActionMapper;
import com.cupk.aiacademy.pojo.AgentPendingActionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisAgentPendingActionStoreTest {
    @Mock
    private AgentPendingActionMapper pendingActionMapper;

    @Test
    void createPersistsPendingActionAsJson() {
        MybatisAgentPendingActionStore store = new MybatisAgentPendingActionStore(pendingActionMapper, new ObjectMapper());

        AgentPendingAction action = store.create("notice.create",
                7,
                Map.of("title", "作业提醒", "classId", 3),
                Duration.ofMinutes(5));

        assertNotNull(action.actionId());
        assertEquals("notice.create", action.actionCode());
        assertEquals(7, action.createdBy());
        ArgumentCaptor<AgentPendingActionEntity> captor = ArgumentCaptor.forClass(AgentPendingActionEntity.class);
        verify(pendingActionMapper).insert(captor.capture());
        AgentPendingActionEntity entity = captor.getValue();
        assertEquals(action.actionId(), entity.getActionId());
        assertTrue(entity.getParametersJson().contains("\"title\":\"作业提醒\""));
        assertTrue(entity.getExpiresAt().isAfter(entity.getCreatedAt()));
    }

    @Test
    void findByIdRestoresParameters() {
        AgentPendingActionEntity entity = new AgentPendingActionEntity();
        entity.setActionId("action-1");
        entity.setActionCode("notice.create");
        entity.setCreatedBy(7);
        entity.setParametersJson("{\"title\":\"作业提醒\",\"classId\":3}");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(pendingActionMapper.selectById("action-1")).thenReturn(entity);
        MybatisAgentPendingActionStore store = new MybatisAgentPendingActionStore(pendingActionMapper, new ObjectMapper());

        Optional<AgentPendingAction> action = store.findById(" action-1 ");

        assertTrue(action.isPresent());
        assertEquals("notice.create", action.get().actionCode());
        assertEquals("作业提醒", action.get().parameters().get("title"));
        assertEquals(3, action.get().parameters().get("classId"));
    }

    @Test
    void findByIdReturnsEmptyForBlankId() {
        MybatisAgentPendingActionStore store = new MybatisAgentPendingActionStore(pendingActionMapper, new ObjectMapper());

        assertFalse(store.findById(" ").isPresent());
    }

    @Test
    void removeDeletesByTrimmedId() {
        MybatisAgentPendingActionStore store = new MybatisAgentPendingActionStore(pendingActionMapper, new ObjectMapper());

        store.remove(" action-1 ");

        verify(pendingActionMapper).deleteById("action-1");
    }
}
