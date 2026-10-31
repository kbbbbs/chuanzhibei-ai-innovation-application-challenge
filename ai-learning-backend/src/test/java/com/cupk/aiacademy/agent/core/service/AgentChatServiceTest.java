package com.cupk.aiacademy.agent.core.service;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.knowledge.KnowledgeContextProvider;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.AgentMessageMapper;
import com.cupk.aiacademy.mapper.AgentSessionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.AgentSession;
import com.cupk.aiacademy.pojo.AgentMessage;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyList;

class AgentChatServiceTest {

    @Test
    void revokedHistoricalEvidenceCannotEnterPromptAfterAgentBindingsChange() throws Exception {
        ChatModelClient model = mock(ChatModelClient.class);
        AgentDefinitionMapper definitions = mock(AgentDefinitionMapper.class);
        AgentSessionMapper sessions = mock(AgentSessionMapper.class);
        AgentMessageMapper messages = mock(AgentMessageMapper.class);
        KnowledgeContextProvider provider = mock(KnowledgeContextProvider.class);
        ObjectMapper json = new ObjectMapper();
        AgentSession session = new AgentSession(); session.setId(9); session.setAgentId(7); session.setStatus(1);
        when(sessions.selectById(9)).thenReturn(session);
        AgentDefinition agent = new AgentDefinition(); agent.setId(7); agent.setStatus(1); agent.setKnowledgeBaseIds("[]");
        when(definitions.selectById(7)).thenReturn(agent);
        RagSearchHit source = new RagSearchHit("c", "d", "old-shared-base", "旧私有资料", "受保护的原文", null, 1, 1, "[1]");
        AgentMessage previous = new AgentMessage(); previous.setRole("assistant"); previous.setContent("受保护的原文[1]");
        previous.setToolName("native_rag"); previous.setToolResult(json.writeValueAsString(
                new KnowledgeContextProvider.Retrieval("受保护的原文", List.of(source), "bm25", "ANSWERED", null)));
        when(messages.selectList(any())).thenReturn(List.of(previous));
        doThrow(new ForbiddenException("资料已撤回")).when(provider).requireReadableEvidence(anyList());
        AgentChatService service = new AgentChatService(model, new AgentChatProperties(), definitions, sessions,
                messages, json, provider, null, null);
        AgentChatRequest request = new AgentChatRequest(); request.setContent("重复之前的答案");
        assertThrows(ForbiddenException.class, () -> service.chat(9, request));
        assertThrows(ForbiddenException.class, () -> service.listMessages(9));
        verifyNoInteractions(model);
    }

    @Test
    void singleTurnRechecksSourceAccessAfterModelCall() {
        ChatModelClient model = mock(ChatModelClient.class);
        AgentDefinitionMapper definitions = mock(AgentDefinitionMapper.class);
        KnowledgeContextProvider provider = mock(KnowledgeContextProvider.class);
        AgentDefinition agent = new AgentDefinition(); agent.setId(7); agent.setStatus(1);
        when(definitions.selectById(7)).thenReturn(agent);
        var hit = new RagSearchHit("c", "d", "base", "资料", "原文", null, 1, 1, "[1]");
        when(provider.retrieveWithEvidence(agent, "问题")).thenReturn(
                new KnowledgeContextProvider.Retrieval("[1]原文", List.of(hit), "bm25", "RETRIEVED", null));
        when(model.call(any(Prompt.class), any(Duration.class))).thenAnswer(invocation -> {
            doThrow(new ForbiddenException("资料已撤回")).when(provider).requireReadableEvidence(anyList());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("原文[1]"))));
        });
        var service = new AgentChatService(model, new AgentChatProperties(), definitions,
                mock(AgentSessionMapper.class), mock(AgentMessageMapper.class), new ObjectMapper(), provider, null, null);
        assertThrows(ForbiddenException.class, () -> service.chatOnce(7, "问题"));
    }

    @Test
    void chatOnceUsesSelectedAgentAndKnowledgeContext() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        AgentDefinitionMapper agentMapper = mock(AgentDefinitionMapper.class);
        KnowledgeContextProvider knowledgeProvider = mock(KnowledgeContextProvider.class);

        AgentDefinition agent = new AgentDefinition();
        agent.setId(7);
        agent.setCode("rag_agent");
        agent.setName("RAG Agent");
        agent.setSystemPrompt("只根据课程资料回答");
        agent.setKnowledgeBaseIds("[\"3e4c620e-e924-4b47-8ba0-d2288658d4d5\"]");
        agent.setStatus(1);
        when(agentMapper.selectById(7)).thenReturn(agent);
        when(knowledgeProvider.retrieve(agent, "SPOOLing 是什么"))
                .thenReturn("[1] 来源：输入输出系统.pptx SPOOLing 可将独占设备改造为共享设备");
        when(chatModelClient.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("SPOOLing 可将独占设备改造为共享设备[1]")))));

        AgentChatService service = new AgentChatService(
                chatModelClient,
                new AgentChatProperties(),
                agentMapper,
                mock(AgentSessionMapper.class),
                mock(AgentMessageMapper.class),
                new ObjectMapper(),
                knowledgeProvider,
                null,
                null
        );

        String answer = service.chatOnce(7, "SPOOLing 是什么");

        assertEquals("SPOOLing 可将独占设备改造为共享设备[1]", answer);
        verify(knowledgeProvider).retrieve(agent, "SPOOLing 是什么");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(promptCaptor.capture(), any(Duration.class));
        String promptContents = promptCaptor.getValue().getContents();
        assertTrue(promptContents.contains("只根据课程资料回答"));
        assertTrue(promptContents.contains("知识库参考资料"));
        assertTrue(promptContents.contains("SPOOLing 可将独占设备改造为共享设备"));
    }
}
