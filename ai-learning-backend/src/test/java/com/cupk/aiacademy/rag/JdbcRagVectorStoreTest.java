package com.cupk.aiacademy.rag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class JdbcRagVectorStoreTest {
 @Test void scanUsesOnlySuppliedAuthorizedRowsAndValidNormalizedVectors() {
  var store=new JdbcRagVectorStore(new ObjectMapper(),new IndexVersionService());
  var good=RagRetrievalServiceTest.row("allowed","text",new double[]{1,0});
  var zero=RagRetrievalServiceTest.row("zero","text",new double[]{0,0});
  var unnormalized=RagRetrievalServiceTest.row("bad-norm","text",new double[]{5,0});
  var result=store.search(List.of(good,zero,unnormalized),new double[]{1,0},"fp","model",5);
  assertEquals(1,result.usableCount());assertEquals("allowed",result.matches().getFirst().chunkId());assertEquals(2,result.skippedCount());
 }
 @Test void chunkVersionMustAgreeWithProjectedIndexWhenMetadataIsAvailable() {
  var row=RagRetrievalServiceTest.row("stale","text",new double[]{1,0});row.put("index_document_version",2);row.put("index_parse_revision",1);
  assertFalse(new IndexVersionService().compatible(row,"fp","model",2));
 }
 @Test void missingSourceProjectionCannotCountAsVerifiedSemanticMetadata() {
  var row=RagRetrievalServiceTest.row("missing","text",new double[]{1,0});row.remove("index_parse_revision");
  assertFalse(new IndexVersionService().compatible(row,"fp","model",2));
 }
 @Test void trailingGarbageAfterVectorJsonIsCorruptionNotAValidVector() {
  var row=RagRetrievalServiceTest.row("bad","text",new double[]{1,0});row.put("vector_json","[1,0] trailing");
  var result=new JdbcRagVectorStore(new ObjectMapper(),new IndexVersionService()).search(List.of(row),new double[]{1,0},"fp","model",5);
  assertEquals(0,result.usableCount());assertTrue(result.matches().isEmpty());assertEquals(1,result.skippedCount());
 }
}
