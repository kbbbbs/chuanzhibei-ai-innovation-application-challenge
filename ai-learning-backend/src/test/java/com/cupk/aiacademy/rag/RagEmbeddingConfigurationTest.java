package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.model.ModelConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RagEmbeddingConfigurationTest {
    @Test void gptEmbeddingUsesCompatibleProtocol() throws Exception {
        var request=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            request.set(exchange.getRequestURI().getPath()+" "+exchange.getRequestHeaders().getFirst("Authorization")+" "+
                    new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body="{\"data\":[{\"index\":0,\"embedding\":[1.0,0.0]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client=new RagEmbeddingClient(true,"http://127.0.0.1:"+server.getAddress().getPort(),"test-secret","text-embedding-3-small",new ObjectMapper());
            assertTrue(client.isConfigured());
            assertEquals(1,client.embed(List.of("test")).size());
            assertTrue(request.get().startsWith("/v1/embeddings Bearer test-secret "));
            assertTrue(request.get().contains("text-embedding-3-small"));
        } finally { server.stop(0); }
    }
    @Test void missingEmbeddingDoesNotBorrowGenerationCredentials() {
        var client=new RagEmbeddingClient(true,"","","",new ObjectMapper());
        assertFalse(client.isConfigured());
        assertEquals("MODEL_NOT_CONFIGURED",assertThrows(ModelConfigurationException.class,()->client.embed(List.of("test"))).code());
    }
}
