package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeKnoraKnowledgeContextProviderTest {
    @Mock
    private WeKnoraService weKnoraService;

    @Test
    void retrieveFormatsSearchSnippetsAndNormalizesKnowledgeBaseIds() {
        WeKnoraKnowledgeContextProvider provider = newProvider();
        AgentDefinition agent = new AgentDefinition();
        agent.setKnowledgeBaseIds("[\"kb-java\",2]");
        when(weKnoraService.search(any())).thenReturn(Result.success(Map.of(
                "data", List.of(
                        Map.of("content", "递归是函数调用自身的方式。"),
                        Map.of("text", "递归必须设置终止条件。")
                )
        )));

        String context = provider.retrieve(agent, "什么是递归");

        assertTrue(context.contains("[1] 递归是函数调用自身的方式。"));
        assertTrue(context.contains("[2] 递归必须设置终止条件。"));
        ArgumentCaptor<com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest> captor =
                ArgumentCaptor.forClass(com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest.class);
        org.mockito.Mockito.verify(weKnoraService).search(captor.capture());
        assertEquals(List.of("kb-java", "2"), captor.getValue().getKnowledgeBaseIds());
        assertEquals("什么是递归", captor.getValue().getQuery());
    }

    private WeKnoraKnowledgeContextProvider newProvider() {
        return new WeKnoraKnowledgeContextProvider(new AgentChatProperties(), new ObjectMapper(), weKnoraService);
    }
}
