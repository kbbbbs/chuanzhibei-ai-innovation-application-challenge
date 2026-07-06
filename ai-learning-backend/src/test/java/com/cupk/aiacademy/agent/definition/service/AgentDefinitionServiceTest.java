package com.cupk.aiacademy.agent.definition.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 定义管理服务测试，覆盖编码去重、默认状态和启用停用逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AgentDefinitionServiceTest {

    @Mock
    private AgentDefinitionMapper agentDefinitionMapper;

    @Mock
    private WeKnoraService weKnoraService;

    @Test
    void createDefinitionTrimsFieldsAndDefaultsActiveStatus() {
        AgentDefinitionService service = newService();
        when(agentDefinitionMapper.selectCount(any())).thenReturn(0L);

        AgentDefinition created = service.createDefinition(request(null));

        assertEquals("java_tutor", created.getCode());
        assertEquals("Java 助教", created.getName());
        assertEquals(1, created.getStatus());
        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentDefinitionMapper).insert(captor.capture());
        assertEquals("deepseek-test", captor.getValue().getModel());
        assertEquals(new BigDecimal("0.30"), captor.getValue().getTemperature());
        assertEquals("[\"kb-java\",2]", captor.getValue().getKnowledgeBaseIds());
        assertEquals("[\"paper_search\"]", captor.getValue().getEnabledTools());
    }

    @Test
    void createDefinitionRejectsDuplicateCode() {
        AgentDefinitionService service = newService();
        when(agentDefinitionMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDefinition(request(1)));

        assertEquals("Agent 编码已存在", exception.getMessage());
    }

    @Test
    void updateStatusRejectsInvalidStatus() {
        AgentDefinitionService service = newService();
        when(agentDefinitionMapper.selectById(1)).thenReturn(existingAgent());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(1, 2));

        assertEquals("status 只能为 0 或 1", exception.getMessage());
    }

    @Test
    void updateStatusDisablesAgent() {
        AgentDefinitionService service = newService();
        when(agentDefinitionMapper.selectById(1)).thenReturn(existingAgent());

        AgentDefinition updated = service.updateStatus(1, 0);

        assertEquals(0, updated.getStatus());
        verify(agentDefinitionMapper).updateById(updated);
    }

    @Test
    void createDefinitionRejectsInvalidKnowledgeBaseIdsJson() {
        AgentDefinitionService service = newService();
        AgentCreateRequest request = request(1);
        request.setKnowledgeBaseIds("not-json");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDefinition(request));

        assertEquals("knowledgeBaseIds 必须是 JSON 数组", exception.getMessage());
    }

    @Test
    void createDefinitionRejectsNonArrayKnowledgeBaseIdsJson() {
        AgentDefinitionService service = newService();
        AgentCreateRequest request = request(1);
        request.setKnowledgeBaseIds("{\"id\":\"kb-java\"}");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDefinition(request));

        assertEquals("knowledgeBaseIds 必须是 JSON 数组", exception.getMessage());
    }

    @Test
    void createDefinitionRejectsInvalidEnabledToolsJson() {
        AgentDefinitionService service = newService();
        AgentCreateRequest request = request(1);
        request.setEnabledTools("course_search");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDefinition(request));

        assertEquals("enabledTools 必须是 JSON 数组", exception.getMessage());
    }

    @Test
    void createDefinitionRejectsNonArrayEnabledToolsJson() {
        AgentDefinitionService service = newService();
        AgentCreateRequest request = request(1);
        request.setEnabledTools("{\"tool\":\"course_search\"}");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDefinition(request));

        assertEquals("enabledTools 必须是 JSON 数组", exception.getMessage());
    }

    @Test
    void uploadMarkdownDocumentUploadsAndAttachesKnowledgeBase() {
        AgentDefinitionService service = newService();
        AgentDefinition agent = existingAgent();
        agent.setKnowledgeBaseIds("[\"kb-old\"]");
        when(agentDefinitionMapper.selectById(1)).thenReturn(agent);
        when(weKnoraService.uploadFile(eq("kb-java"), any(), eq("intro.md")))
                .thenReturn(Result.success(Map.of("documentId", "doc-1")));
        MockMultipartFile file = new MockMultipartFile("file", "intro.md",
                "text/markdown", "# Java\n多态说明".getBytes());

        Map<String, Object> result = service.uploadMarkdownDocument(1, " kb-java ", file);

        assertEquals(1, result.get("agentId"));
        assertEquals("kb-java", result.get("knowledgeBaseId"));
        assertEquals("intro.md", result.get("fileName"));
        assertTrue((Boolean) result.get("attached"));
        assertEquals("[\"kb-old\",\"kb-java\"]", result.get("knowledgeBaseIds"));
        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentDefinitionMapper).updateById(captor.capture());
        assertEquals("[\"kb-old\",\"kb-java\"]", captor.getValue().getKnowledgeBaseIds());
    }

    @Test
    void uploadMarkdownDocumentDoesNotAttachDuplicateKnowledgeBase() {
        AgentDefinitionService service = newService();
        AgentDefinition agent = existingAgent();
        agent.setKnowledgeBaseIds("[\"kb-java\"]");
        when(agentDefinitionMapper.selectById(1)).thenReturn(agent);
        when(weKnoraService.uploadFile(eq("kb-java"), any(), eq("intro.markdown")))
                .thenReturn(Result.success(Map.of("documentId", "doc-1")));
        MockMultipartFile file = new MockMultipartFile("file", "intro.markdown",
                "text/markdown", "# Java".getBytes());

        Map<String, Object> result = service.uploadMarkdownDocument(1, "kb-java", file);

        assertFalse((Boolean) result.get("attached"));
        assertEquals("[\"kb-java\"]", result.get("knowledgeBaseIds"));
        verify(agentDefinitionMapper, never()).updateById(any(AgentDefinition.class));
    }

    @Test
    void uploadMarkdownDocumentRejectsNonMarkdownFile() {
        AgentDefinitionService service = newService();
        when(agentDefinitionMapper.selectById(1)).thenReturn(existingAgent());
        MockMultipartFile file = new MockMultipartFile("file", "intro.txt",
                "text/plain", "Java".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.uploadMarkdownDocument(1, "kb-java", file));

        assertEquals("只支持上传 Markdown 文档(.md/.markdown)", exception.getMessage());
        verify(weKnoraService, never()).uploadFile(any(), any(), any());
    }

    private AgentDefinitionService newService() {
        return new AgentDefinitionService(agentDefinitionMapper, new ObjectMapper(), weKnoraService);
    }

    private AgentCreateRequest request(Integer status) {
        AgentCreateRequest request = new AgentCreateRequest();
        request.setCode("  java_tutor  ");
        request.setName("  Java 助教  ");
        request.setDescription("Java 课程答疑");
        request.setSystemPrompt("你是 Java 课程助教。");
        request.setModel("  deepseek-test  ");
        request.setTemperature(new BigDecimal("0.30"));
        request.setKnowledgeBaseIds("[  \"kb-java\" , 2 ]");
        request.setEnabledTools("[ \"paper_search\" ]");
        request.setStatus(status);
        return request;
    }

    private AgentDefinition existingAgent() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(1);
        agent.setCode("java_tutor");
        agent.setName("Java 助教");
        agent.setStatus(1);
        return agent;
    }
}
