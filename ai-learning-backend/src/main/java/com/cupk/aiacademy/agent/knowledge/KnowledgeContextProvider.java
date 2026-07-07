package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.pojo.AgentDefinition;

public interface KnowledgeContextProvider {
    String retrieve(AgentDefinition agent, String currentUserContent);
}
