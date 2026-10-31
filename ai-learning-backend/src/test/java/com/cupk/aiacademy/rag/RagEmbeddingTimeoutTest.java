package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class RagEmbeddingTimeoutTest {
    @Test void configuredTimeoutIsAppliedToActualEmbeddingRequest() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);
        server.createContext("/",exchange->{
            try { Thread.sleep(5000);exchange.sendResponseHeaders(503,-1); }
            catch(Exception ignored){} finally {exchange.close();}
        });
        server.start();
        try {
            var client=new RagEmbeddingClient(true,"http://127.0.0.1:"+server.getAddress().getPort(),"test-key","bge-test",new ObjectMapper(),1);
            assertTimeoutPreemptively(Duration.ofSeconds(3),()->{
                var error=assertThrows(IllegalStateException.class,()->client.embed(List.of("test")));
                assertFalse(error.getMessage().contains("test-key"));
            });
        } finally {server.stop(0);executor.shutdownNow();}
    }
}
