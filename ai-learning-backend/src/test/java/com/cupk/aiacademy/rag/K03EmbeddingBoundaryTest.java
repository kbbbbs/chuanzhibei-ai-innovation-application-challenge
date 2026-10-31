package com.cupk.aiacademy.rag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class K03EmbeddingBoundaryTest {
 @Test void fingerprintCanonicalizesEquivalentEndpointAndIgnoresCredentialRotation(){
  var a=new RagEmbeddingClient(true,"https://embedding.example.test/v1","first-private-key","model",new ObjectMapper());
  var b=new RagEmbeddingClient(true,"https://embedding.example.test/v1/","second-private-key","model",new ObjectMapper());
  assertEquals(a.fingerprint(),b.fingerprint());
 }
 @Test void aStalledBodyAfterSuccessfulHeadersIsStillBoundedByTheConfiguredTimeout() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var threads=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(threads);
  server.createContext("/",exchange->{try{exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write('{');exchange.getResponseBody().flush();Thread.sleep(5000);}catch(Exception ignored){}finally{exchange.close();}});server.start();
  try{
   var client=new RagEmbeddingClient(true,"http://127.0.0.1:"+server.getAddress().getPort(),"test-key","model",new ObjectMapper(),1);
   assertTimeoutPreemptively(Duration.ofSeconds(3),()->assertThrows(IllegalStateException.class,()->client.embed(List.of("page"))));
  }finally{server.stop(0);threads.shutdownNow();}
 }
}
