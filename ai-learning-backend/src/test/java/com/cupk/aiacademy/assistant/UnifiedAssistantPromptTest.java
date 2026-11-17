package com.cupk.aiacademy.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.core.prompt.AgentPromptAssembler;
import com.cupk.aiacademy.agent.knowledge.KnowledgeContextProvider;
import com.cupk.aiacademy.pojo.AgentDefinition;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;

import java.math.BigDecimal;
import java.util.List;

class UnifiedAssistantPromptTest {
    @Test
    void configuredPersonaAndOptionsApplyWithoutUsingDefinitionLibraryBindings() {
        var provider = mock(KnowledgeContextProvider.class);
        var assembler =
                new AgentPromptAssembler(
                        new AgentChatProperties(),
                        new AgentConfigurationParser(
                                new com.fasterxml.jackson.databind.ObjectMapper()),
                        provider);
        var definition = new AgentDefinition();
        definition.setSystemPrompt("使用教学案例解释");
        definition.setModel("configured-generation-model");
        definition.setTemperature(new BigDecimal("0.25"));
        definition.setKnowledgeBaseIds("[\"unrelated-private-library\"]");
        var prompt =
                assembler.buildAssistantPrompt(
                        definition,
                        List.of(new SystemMessage("仅使用当前课程范围"), new UserMessage("解释梯度")));
        assertTrue(prompt.getInstructions().getFirst().getContent().contains("使用教学案例解释"));
        assertEquals("configured-generation-model", prompt.getOptions().getModel());
        assertEquals(0.25, prompt.getOptions().getTemperature());
        assertEquals(3, prompt.getInstructions().size());
        verifyNoInteractions(provider);
    }
}
