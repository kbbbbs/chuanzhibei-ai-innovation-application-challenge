package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Evaluation tooling contracts; no paid calls. Written before tooling implementation. */
class K03EvaluationSupportTest {
 @TempDir Path temp;
 final ObjectMapper json=new ObjectMapper();
 @Test void diskCacheReusesOnlyExactInputsAndReturnsDefensiveCopies() throws Exception {
  var provider=provider("fp","model");when(provider.embed(anyList())).thenReturn(List.of(new double[]{1,0},new double[]{0,1}));
  Path file=temp.resolve("cache.json");var first=new K03TuningSupport.RealCache(provider,json,file,2);
  first.prime(List.of("question","question "));assertEquals(1,first.realCalls);assertEquals(2,first.realInputs);
  var replayProvider=provider("fp","model");var replay=new K03TuningSupport.RealCache(replayProvider,json,file,2);
  var values=replay.embed(List.of("question","question "));assertArrayEquals(new double[]{1,0},values.getFirst());assertArrayEquals(new double[]{0,1},values.get(1));
  values.getFirst()[0]=0;assertArrayEquals(new double[]{1,0},replay.embed(List.of("question")).getFirst());
  verify(replayProvider,never()).embed(anyList());assertEquals(2,replay.loadedVectors);
  String disk=Files.readString(file);assertFalse(disk.contains("question"),"Cache contains SHA keys and vectors, not course/query text");
 }
 @Test void diskCacheCannotCrossModelFingerprintOrDimensionBoundary() throws Exception {
  var provider=provider("fp","model");when(provider.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
  Path file=temp.resolve("cache.json");new K03TuningSupport.RealCache(provider,json,file,2).prime(List.of("q"));
  for(var other:List.of(provider("changed","model"),provider("fp","other-model"))) {
   var cache=new K03TuningSupport.RealCache(other,json,file,2);assertEquals(0,cache.loadedVectors);assertTrue(cache.incompatibleDiskCache);
  }
  var dimension=new K03TuningSupport.RealCache(provider,json,file,3);assertEquals(0,dimension.loadedVectors);assertTrue(dimension.incompatibleDiskCache);
 }
 @Test void invalidCachedVectorCannotBeUsedAsRealEvidence() throws Exception {
  Path file=temp.resolve("invalid.json");Files.writeString(file,"{\"version\":1,\"fingerprint\":\"fp\",\"model\":\"model\",\"dimensions\":2,\"normalization\":\"L2\",\"requestProtocol\":\"plain-text/v1\",\"vectors\":[{\"inputSha256\":\""+"a".repeat(64)+"\",\"vector\":[0,0]}]}");
  assertThrows(IllegalStateException.class,()->new K03TuningSupport.RealCache(provider("fp","model"),json,file,2));
 }
 @Test void diskCacheDoesNotCrossNormalizationOrInputProtocolBoundaries() throws Exception {
  var provider=provider("fp","model");when(provider.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
  Path file=temp.resolve("cache.json");new K03TuningSupport.RealCache(provider,json,file,2).prime(List.of("q"));
  String original=Files.readString(file);
  for(String modified:List.of(original.replace("\"L2\"","\"unknown\""),original.replace("plain-text/v1","instruction-prefix/v2"))) {
   Files.writeString(file,modified);var cache=new K03TuningSupport.RealCache(provider,json,file,2);
   assertEquals(0,cache.loadedVectors);assertTrue(cache.incompatibleDiskCache);
  }
 }
 @Test void gridCanReadOnlyThePreregisteredTuningIdsAndCannotScoreAcceptance() throws Exception {
  var data=json.readTree("{\"questions\":[{\"id\":\"t\",\"split\":\"tuning\",\"answerable\":true,\"query\":\"q\"},{\"id\":\"a\",\"split\":\"acceptance\",\"answerable\":true,\"query\":\"held out\"}]}");
  var plan=json.readTree("{\"questionCount\":1,\"allowedQuestionIds\":[\"t\"]}");
  assertEquals(List.of("t"),K03TuningSupport.tuningQueries(data,plan).stream().map(q->q.path("id").asText()).toList());
  var invalid=json.readTree("{\"questionCount\":1,\"allowedQuestionIds\":[\"a\"]}");
  assertThrows(IllegalArgumentException.class,()->K03TuningSupport.tuningQueries(data,invalid));
 }
 @Test void selectionUsesRegisteredMetricsThenSmallerCandidateCountAndK() {
  var best=new K03TuningSupport.Score(10,5,8,6,.7);
  var tied=new K03TuningSupport.Score(60,10,8,6,.7);
  var lowerTop5=new K03TuningSupport.Score(1,5,7,7,.7);
  assertSame(best,K03TuningSupport.select(List.of(lowerTop5,tied,best)));
 }
 @Test void explicitConfigurationIsInjectedAndChangesProductionCandidateCount() {
  var provider=provider("fp","model");when(provider.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
  var nativeService=mock(NativeRagService.class);
  var narrow=K03TuningSupport.configure(nativeService,provider,json,10,1);
  verify(nativeService).configureRetrieval(same(narrow));
  var rows=List.of(RagRetrievalServiceTest.row("a","alpha",new double[]{1,0}),RagRetrievalServiceTest.row("b","beta",new double[]{1,0}));
  assertEquals(1,narrow.retrieve("rectifier",rows,5).ranked().size());
  var wider=K03TuningSupport.configure(nativeService,provider,json,10,2);
  assertEquals(2,wider.retrieve("rectifier",rows,5).ranked().size());
  assertEquals(1.0/11,narrow.retrieve("rectifier",rows,5).ranked().getFirst().score(),1e-12);
 }
 private RagEmbeddingClient provider(String fingerprint,String model) {
  var provider=mock(RagEmbeddingClient.class);when(provider.isConfigured()).thenReturn(true);when(provider.isRequested()).thenReturn(true);when(provider.fingerprint()).thenReturn(fingerprint);when(provider.modelName()).thenReturn(model);return provider;
 }
}
