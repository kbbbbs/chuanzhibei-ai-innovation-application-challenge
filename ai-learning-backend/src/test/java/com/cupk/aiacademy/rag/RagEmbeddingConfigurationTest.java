package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.model.ModelConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RagEmbeddingConfigurationTest {
    @Test void gptEmbeddingCannotBypassRuntimePolicy() {
        var client=new RagEmbeddingClient(true,"https://models.example.test","test-secret","text-embedding-3-small",new ObjectMapper());
        assertFalse(client.isConfigured());
        assertEquals("MODEL_BLOCKED",assertThrows(ModelConfigurationException.class,()->client.embed(List.of("test"))).code());
    }
    @Test void missingEmbeddingDoesNotBorrowGenerationCredentials() {
        var client=new RagEmbeddingClient(true,"","","",new ObjectMapper());
        assertFalse(client.isConfigured());
        assertEquals("MODEL_NOT_CONFIGURED",assertThrows(ModelConfigurationException.class,()->client.embed(List.of("test"))).code());
    }
}
