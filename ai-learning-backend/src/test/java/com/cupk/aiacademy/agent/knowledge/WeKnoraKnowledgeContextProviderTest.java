package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeKnoraKnowledgeContextProviderTest {
    private static final String KNOWLEDGE_BASE_ID = "3e4c620e-e924-4b47-8ba0-d2288658d4d5";

    @Test
    void retrievesUuidKnowledgeBaseAndKeepsSourceTitle() {
        WeKnoraService weKnoraService = mock(WeKnoraService.class);
        AgentChatProperties properties = new AgentChatProperties();
        properties.setKnowledgeContextMaxSnippets(3);
        properties.setKnowledgeContextMaxCharacters(1200);
        WeKnoraKnowledgeContextProvider provider = new WeKnoraKnowledgeContextProvider(
                properties, new ObjectMapper(), weKnoraService);

        AgentDefinition agent = new AgentDefinition();
        agent.setKnowledgeBaseIds("[\"" + KNOWLEDGE_BASE_ID + "\"]");
        when(weKnoraService.search(any())).thenReturn(Result.success(List.of(Map.of(
                "knowledge_title", "7 输入输出系统.pptx",
                "content", "SPOOLing 通过输入井、输出井和缓冲区将独占设备在逻辑上改造成共享设备。",
                "score", 0.72
        ))));

        String context = provider.retrieve(agent, "SPOOLing 是什么");

        assertTrue(context.contains("[1] 来源：7 输入输出系统.pptx"));
        assertTrue(context.contains("SPOOLing 通过输入井"));
        verify(weKnoraService).search(any());
    }

    @Test
    void ignoresLegacyNumericKnowledgeBaseIds() {
        WeKnoraService weKnoraService = mock(WeKnoraService.class);
        WeKnoraKnowledgeContextProvider provider = new WeKnoraKnowledgeContextProvider(
                new AgentChatProperties(), new ObjectMapper(), weKnoraService);
        AgentDefinition agent = new AgentDefinition();
        agent.setKnowledgeBaseIds("[1]");

        assertEquals("", provider.retrieve(agent, "测试问题"));
        verifyNoInteractions(weKnoraService);
    }
}
