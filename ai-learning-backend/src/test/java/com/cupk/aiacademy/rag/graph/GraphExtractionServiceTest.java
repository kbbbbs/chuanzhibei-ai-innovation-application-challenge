package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.core.llm.ChatModelTimeoutException;
import com.cupk.aiacademy.model.ModelConfigurationException;
import com.cupk.aiacademy.rag.RagAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphExtractionServiceTest {
    @Test void reportsTimeoutAndConfiguredUnavailableModelWithSafeSpecificStatus() {
        assertModelFailure(new ChatModelTimeoutException("private endpoint response", null), "504", "超时");
        assertModelFailure(new ModelConfigurationException("GENERATION_CONFIGURATION"), "503", "配置");
    }

    private void assertModelFailure(RuntimeException failure, String expectedCode, String expectedMessage) {
        var graph = mock(GraphService.class);
        var model = mock(ChatModelClient.class);
        when(model.configuredModel()).thenReturn("configured-generation-model");
        when(graph.get("g",true)).thenReturn(Map.of("revision",1));
        when(graph.sources("g")).thenReturn(Map.of("chunks",List.of(Map.of("id","chunk","content","Actual source"))));
        when(graph.beginExtraction(eq("g"),eq(1),anyString(),anyString(),anyString())).thenReturn("task");
        when(model.call(any(Prompt.class),any(Duration.class))).thenThrow(failure);
        var extraction = new GraphExtractionService(graph,model,new ObjectMapper());
        RagAccessException error = assertThrows(RagAccessException.class,()->extraction.extract("g",1));
        assertEquals(expectedCode,error.code());
        assertTrue(error.getMessage().contains(expectedMessage));
        assertFalse(error.getMessage().contains("private endpoint"));
        verify(graph).finishExtraction("task",null,error.getMessage());
        verify(model).call(any(Prompt.class),eq(Duration.ofSeconds(90)));
        verify(graph,never()).saveDraft(anyString(),any());
    }
    @Test void validatesReferencedQuotesBeforeSavingCandidateDraft() throws Exception {
        var graph = mock(GraphService.class);
        var model = mock(ChatModelClient.class);
        when(model.configuredModel()).thenReturn("configured-generation-model");
        var source = Map.<String,Object>of("id","chunk","chunkId","chunk","content","Supervised learning includes classification.");
        when(graph.get("g",true)).thenReturn(Map.of("revision",1));
        when(graph.sources("g")).thenReturn(Map.of("chunks",List.of(source)));
        String payload = new ObjectMapper().writeValueAsString(Map.of("nodes", GraphServiceTest.nodes(), "edges", GraphServiceTest.edges(),
                "evidence", List.of(Map.of("chunkId","chunk","quote","Supervised learning"))));
        when(model.call(any(Prompt.class),any(Duration.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(payload)))));
        when(graph.saveDraft(eq("g"),any())).thenReturn(Map.of("revision",2));
        when(graph.beginExtraction(eq("g"),eq(1),anyString(),anyString(),anyString())).thenReturn("task");
        var extraction = new GraphExtractionService(graph,model,new ObjectMapper());
        assertEquals(2,extraction.extract("g",1).get("revision"));
        verify(graph).saveDraft(eq("g"),any());
        verify(graph).finishExtraction("task",2,null);
    }

    @Test void rejectsHallucinatedQuoteAndDoesNotOverwriteDraft() {
        var graph = mock(GraphService.class);
        var model = mock(ChatModelClient.class);
        when(model.configuredModel()).thenReturn("configured-generation-model");
        when(graph.get("g",true)).thenReturn(Map.of("revision",1));
        when(graph.sources("g")).thenReturn(Map.of("chunks",List.of(Map.of("id","chunk","content","Actual source"))));
        when(graph.beginExtraction(eq("g"),eq(1),anyString(),anyString(),anyString())).thenReturn("task");
        when(model.call(any(Prompt.class),any(Duration.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"nodes\":[],\"edges\":[],\"evidence\":[{\"chunkId\":\"chunk\",\"quote\":\"invented text\"}]}")))));
        var extraction = new GraphExtractionService(graph,model,new ObjectMapper());
        assertThrows(RagAccessException.class,()->extraction.extract("g",1));
        verify(graph,never()).saveDraft(anyString(),any());
        verify(graph).finishExtraction(eq("task"),isNull(),anyString());
    }
}
