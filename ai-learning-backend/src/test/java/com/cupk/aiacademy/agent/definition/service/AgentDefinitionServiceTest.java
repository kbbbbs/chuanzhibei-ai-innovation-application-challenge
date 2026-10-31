package com.cupk.aiacademy.agent.definition.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.rag.NativeRagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AgentDefinitionServiceTest {
    private static final String KNOWLEDGE_BASE_ID = "3e4c620e-e924-4b47-8ba0-d2288658d4d5";

    @Test
    void normalizesAndDeduplicatesNativeKnowledgeBaseIds() {
        AgentDefinitionService service = service();
        AgentCreateRequest request = request();
        request.setKnowledgeBaseIds("[\"" + KNOWLEDGE_BASE_ID + "\",\"" + KNOWLEDGE_BASE_ID + "\"]");

        AgentDefinition created = service.createDefinition(request);

        assertEquals("[\"" + KNOWLEDGE_BASE_ID + "\"]", created.getKnowledgeBaseIds());
    }

    @Test
    void rejectsLegacyNumericKnowledgeBaseIds() {
        AgentDefinitionService service = service();
        AgentCreateRequest request = request();
        request.setKnowledgeBaseIds("[1]");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createDefinition(request));

        assertEquals("knowledgeBaseIds 必须是本项目知识库 UUID 字符串数组", error.getMessage());
    }

    @Test
    void rejectsNonCanonicalUuid() {
        AgentDefinitionService service = service();
        AgentCreateRequest request = request();
        request.setKnowledgeBaseIds("[\"3e4c620e-e924-4b47-8ba0-d2288658d4d5-extra\"]");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createDefinition(request));

        assertEquals("knowledgeBaseId 必须是有效的本项目知识库 UUID", error.getMessage());
    }

    private AgentDefinitionService service() {
        return new AgentDefinitionService(
                mock(AgentDefinitionMapper.class),
                new ObjectMapper(),
                mock(NativeRagService.class));
    }

    private AgentCreateRequest request() {
        AgentCreateRequest request = new AgentCreateRequest();
        request.setCode("rag_agent");
        request.setName("RAG Agent");
        request.setTemperature(new BigDecimal("0.70"));
        request.setStatus(1);
        return request;
    }
}
