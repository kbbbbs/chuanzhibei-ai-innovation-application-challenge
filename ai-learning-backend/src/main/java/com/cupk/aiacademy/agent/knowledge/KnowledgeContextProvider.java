package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.rag.RagSearchHit;
import java.util.List;

public interface KnowledgeContextProvider {
    String retrieve(AgentDefinition agent, String currentUserContent);

    default void requireReadableEvidence(List<RagSearchHit> citations) {}

    /** The evidence is captured before generation, never reconstructed afterwards. */
    default Retrieval retrieveWithEvidence(AgentDefinition agent, String query) {
        return new Retrieval(retrieve(agent, query), List.of(), "none", "NO_KNOWLEDGE", null);
    }

    record Retrieval(String context, List<RagSearchHit> citations, String mode, String status, String warning) {
        public Retrieval {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
        public static Retrieval empty() {
            return new Retrieval("", List.of(), "none", "NO_KNOWLEDGE", null);
        }
    }
}
