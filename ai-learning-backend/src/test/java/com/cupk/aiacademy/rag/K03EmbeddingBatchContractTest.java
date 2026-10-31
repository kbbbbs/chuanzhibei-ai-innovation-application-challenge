package com.cupk.aiacademy.rag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class K03EmbeddingBatchContractTest {
 @Test void selectedVlModelUsesEightItemBatchesAndKeepsExplicitIndexMapping() throws Exception {
  var json=new ObjectMapper();var sizes=new ArrayList<Integer>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/embeddings",exchange->{
   var body=json.readTree(exchange.getRequestBody().readAllBytes());var inputs=body.path("input");sizes.add(inputs.size());var data=new ArrayList<Map<String,Object>>();
   // Reproduce the observed provider contract: indices restart within each internal eight-item group.
   // Reverse wire order too: the client must use the index, never silently pair by arrival order.
   for(int i=inputs.size()-1;i>=0;i--){int global=Integer.parseInt(inputs.get(i).asText().substring(6));data.add(Map.of("index",i%8,"embedding",List.of(global+1.0,1.0)));}
   byte[] response=json.writeValueAsBytes(Map.of("data",data));exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
  });server.start();
  try {
   var client=new RagEmbeddingClient(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","synthetic-contract-key","Qwen/Qwen3-VL-Embedding-8B",json,5);
   var input=new ArrayList<String>();for(int i=0;i<20;i++)input.add("input-"+i);
   var result=client.embed(input);assertEquals(List.of(8,8,4),sizes);assertEquals(20,result.size());
   for(int i=0;i<result.size();i++){double norm=Math.sqrt((i+1.0)*(i+1.0)+1);assertArrayEquals(new double[]{(i+1.0)/norm,1.0/norm},result.get(i),1e-12);}
  } finally {server.stop(0);}
 }
 @Test void duplicateIndicesInsideEightItemReplyAreStillRejected() throws Exception {
  var json=new ObjectMapper();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/embeddings",exchange->{var body=json.readTree(exchange.getRequestBody().readAllBytes());var data=new ArrayList<Map<String,Object>>();
   for(var ignored:body.path("input"))data.add(Map.of("index",0,"embedding",List.of(1.0,0.0)));
   byte[] response=json.writeValueAsBytes(Map.of("data",data));exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
  try {var client=new RagEmbeddingClient(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","synthetic-contract-key","Qwen/Qwen3-VL-Embedding-8B",json,5);
   assertThrows(IllegalStateException.class,()->client.embed(List.of("a","b")));
  } finally {server.stop(0);}
 }
}
