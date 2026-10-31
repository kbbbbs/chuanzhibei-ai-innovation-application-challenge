package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class K03SearchContractTest {
 @TempDir Path temp;JdbcTemplate db;NativeRagService service;RagEmbeddingClient embeddings;String base;
 @BeforeEach void setup(){
  db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  embeddings=mock(RagEmbeddingClient.class);when(embeddings.fingerprint()).thenReturn("model-A");when(embeddings.modelName()).thenReturn("test-model");
  when(embeddings.isConfigured()).thenReturn(true);when(embeddings.isRequested()).thenReturn(true);
  when(embeddings.embed(anyList())).thenAnswer(call->((List<?>)call.getArgument(0)).stream().map(x->new double[]{1,0}).toList());
  service=new NativeRagService(db,new RagDocumentParser(),embeddings,new ObjectMapper(),temp.toString());service.initialize();
  var user=new AuthUtils.LoginUser();user.setUserId(1);user.setRoleCode("teacher");UserContext.set(user);
  base=((Map<?,?>)service.createKnowledgeBase("K03","test").getData()).get("id").toString();
 }
 @AfterEach void cleanup(){UserContext.clear();db.execute("DROP ALL OBJECTS");}
 String add(String content){var result=service.uploadFile(base,content.getBytes(StandardCharsets.UTF_8),UUID.randomUUID()+".txt");assertEquals("200",result.getCode(),result.getMsg());return ((Map<?,?>)result.getData()).get("id").toString();}
 Map<String,Object> search(String query){var result=service.search(new RagSearchRequest(List.of(base),query,5));assertEquals("200",result.getCode(),result.getMsg());return new ObjectMapper().convertValue(result.getData(),Map.class);}
 @Test void semanticRankingHasNoLegacyPointSixTwoCutoffAndReturnsActualIndexMetadata(){
  String doc=add("Source describes parameter updates using a negative derivative.");
  when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{.5,Math.sqrt(.75)}));
  var found=search("如何优化模型？");assertFalse(((List<?>)found.get("hits")).isEmpty());
  assertEquals("HYBRID",found.get("actualMode"));assertNull(found.get("degradedReason"));
  var versions=(List<?>)found.get("indexVersions");assertEquals(1,versions.size());assertEquals(doc,((Map<?,?>)versions.getFirst()).get("documentId"));
  var hit=(Map<?,?>)((List<?>)found.get("hits")).getFirst();assertNotNull(hit.get("indexVersion"));assertEquals(1,hit.get("documentVersion"));
 }
 @Test void vectorFailureHasStableReasonAndRetainsKeywordResults(){
  add("Gradient descent minimizes a loss function.");when(embeddings.embed(anyList())).thenThrow(new IllegalStateException("private provider detail"));
  var found=search("gradient");assertEquals("KEYWORD",found.get("actualMode"));assertEquals("EMBEDDING_UNAVAILABLE",found.get("degradedReason"));
  assertFalse(((List<?>)found.get("hits")).isEmpty());assertFalse(found.toString().contains("private provider detail"));
 }
 @Test void emptyLibraryDoesNotCallQueryEmbeddingAndDoesNotClaimHybrid(){
  clearInvocations(embeddings);var found=search("gradient");verify(embeddings,never()).embed(anyList());
  assertEquals("NONE",found.get("actualMode"));assertTrue(((List<?>)found.get("hits")).isEmpty());
 }
 @Test void revokedOrChangedIndexDuringQueryCannotReturnAStaleSnapshot(){
  String doc=add("Gradient descent minimizes loss.");
  when(embeddings.embed(anyList())).thenAnswer(call->{db.update("UPDATE native_rag_document SET active_index_version=NULL WHERE id=?",doc);return List.of(new double[]{1,0});});
  var result=service.search(new RagSearchRequest(List.of(base),"gradient",5));assertEquals("409",result.getCode());assertNull(result.getData());
 }
 @Test void reindexDoesNotRelabelExistingChunkOrParserProvenance(){
  String doc=add("Gradient descent minimizes loss.");
  db.update("UPDATE native_rag_index_version SET chunk_revision='formula-chunker-stable',parser_revision='parser-v7' WHERE document_id=?",doc);
  assertEquals("200",service.reindexDocument(doc).getCode());
  var index=db.queryForMap("SELECT iv.* FROM native_rag_document d JOIN native_rag_index_version iv ON d.active_index_version=iv.id WHERE d.id=?",doc);
  assertEquals("formula-chunker-stable",index.get("chunk_revision"));assertEquals("parser-v7",index.get("parser_revision"));
 }
 @Test void rebuildingVectorsPreservesOriginalChunkRangesForQuestionAndFigureAssociations(){
  String doc=add("Gradient descent minimizes loss.");
  db.update("UPDATE native_rag_chunk SET char_start=17,char_end=117 WHERE document_id=?",doc);
  assertEquals("200",service.reindexDocument(doc).getCode());
  var chunk=db.queryForMap("SELECT c.char_start,c.char_end FROM native_rag_chunk c JOIN native_rag_document d ON d.active_index_version=c.index_version WHERE d.id=?",doc);
  assertEquals(17,chunk.get("char_start"));assertEquals(117,chunk.get("char_end"));
 }
}
