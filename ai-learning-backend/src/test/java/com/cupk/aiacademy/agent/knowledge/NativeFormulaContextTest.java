package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.rag.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeFormulaContextTest {
    @Test void budgetSkipsWholeFormulaInsteadOfSendingHalfToAgent() {
        var knowledge=mock(NativeRagService.class);var limits=new AgentChatProperties();limits.setKnowledgeContextMaxCharacters(220);
        String formula="$$"+"x_i+".repeat(100)+"y$$";
        when(knowledge.search(any())).thenReturn(Result.success(new RagSearchResponse(List.of(
                new RagSearchHit("c","d","b","教材","定义\n"+formula+"\n变量",1,1,1,"[1]")),"bm25",null)));
        var agent=new AgentDefinition();agent.setKnowledgeBaseIds("[\"b\"]");
        var retrieval=new NativeKnowledgeContextProvider(knowledge,new ObjectMapper(),limits).retrieveWithEvidence(agent,"公式");
        assertFalse(retrieval.context().contains("$$"));
        assertTrue(retrieval.context().length()<=220);
    }
}
