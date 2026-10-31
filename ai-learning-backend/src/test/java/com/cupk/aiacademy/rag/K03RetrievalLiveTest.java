package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.rag.ingestion.*;
import com.cupk.aiacademy.rag.ocr.*;
import com.cupk.aiacademy.rag.formula.FormulaText;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.model.ModelInvocationException;
import com.fasterxml.jackson.databind.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Paid opt-in K03 acceptance. Source text and vectors stay under ignored target/temp. */
@EnabledIfEnvironmentVariable(named="K03_LIVE_RETRIEVAL_CHECK",matches="true")
class K03RetrievalLiveTest {
 @TempDir Path temp;
 final ObjectMapper json=new ObjectMapper();
 record Source(String key,String file,int originalPage,String sourceSha,byte[] pdf,String predictedText,String imageSha,CapturedOcr captured) { }
 record CapturedOcr(String raw,String normalized) { }

 @Test void frozenCourseQuestionsUseRealBatchedEmbeddingsAndPersistedHybridRetrieval() throws Exception {
  Path root=Path.of("..").toAbsolutePath().normalize(),output=Path.of("target/k03-retrieval");Files.createDirectories(output);
  Path datasetPath=root.resolve("docs/competition/development/K03-evaluation-dataset.json");
  byte[] datasetBytes=Files.readAllBytes(datasetPath);var dataset=json.readTree(datasetBytes);
  var frozen=json.readTree(Files.readString(root.resolve("docs/competition/development/K03-evaluation-freeze.json")));
  assertEquals(frozen.path("sha256").asText(),sha(datasetBytes),"Frozen question set changed");
  String run=setting("K03_RETRIEVAL_RUN");if(run.isBlank())run="original";
  assertTrue(List.of("original","tuning","followup").contains(run),"Unknown retrieval run mode");
  var plan=json.readTree(Files.readString(root.resolve("docs/competition/development/K03-retrieval-tuning-plan.json")));
  assertEquals(plan.path("originalDatasetSha256").asText(),sha(datasetBytes));
  JsonNode questions=dataset.path("questions");
  if(run.equals("tuning"))questions=json.valueToTree(K03TuningSupport.tuningQueries(dataset,plan));
  if(run.equals("followup")) {
   datasetPath=root.resolve("docs/competition/development/K03-evaluation-followup-dataset.json");datasetBytes=Files.readAllBytes(datasetPath);dataset=json.readTree(datasetBytes);
   frozen=json.readTree(Files.readString(root.resolve("docs/competition/development/K03-evaluation-followup-freeze.json")));
   assertEquals(frozen.path("sha256").asText(),sha(datasetBytes));assertEquals(plan.path("subsequentVerification").path("sha256").asText(),sha(datasetBytes));
   questions=dataset.path("questions");
  }
  String outputName=run.equals("original")?"live-report.json":run.equals("tuning")?"tuning-report.json":"followup-report.json";
  if(run.equals("followup"))assertFalse(Files.exists(output.resolve(outputName)),"Follow-up may be executed only once; retain the observed result");
  var report=new LinkedHashMap<String,Object>();report.put("run",run);report.put("tuningPlanSha256",sha(Files.readAllBytes(root.resolve("docs/competition/development/K03-retrieval-tuning-plan.json"))));var results=new ArrayList<Map<String,Object>>();var skippedPages=new ArrayList<Map<String,Object>>();
  report.put("status","FAIL");report.put("datasetSha256",sha(datasetBytes));report.put("questions",results);report.put("excludedPages",skippedPages);
  report.put("scope","Original PDF single-page copies. Safe native pages plus captured actual K02 OCR; missing OCR pages excluded. Copy page=1 mapped to original page explicitly.");
  report.put("noAnswerPolicy","Retrieval observations only; no K04 abstention or answer-model accuracy claimed.");
  long started=System.nanoTime();JdbcTemplate db=null;K03TuningSupport.RealCache client=null;
  try {
   var real=new RagEmbeddingClient(true,setting("RAG_EMBEDDING_BASE_URL"),setting("RAG_EMBEDDING_API_KEY","RAG_EMBEDDING_APIKEY","SILICONFLOW_API_KEY"),setting("RAG_EMBEDDING_MODEL"),json,120);
   assertTrue(real.isConfigured(),"Private embedding configuration unavailable");assertEquals("Qwen/Qwen3-VL-Embedding-8B",real.modelName());
   report.put("embeddingModel",real.modelName());report.put("embeddingFingerprint",real.fingerprint());client=new K03TuningSupport.RealCache(real,json,output.resolve("real-vectors-private.json"),4096);
   var corpus=corpus(root,dataset,skippedPages);assertTrue(corpus.size()>30,"A tiny corpus would make top5 misleading");assertTrue(corpus.size()<200,"Single library limit");
   report.put("sourcePageCount",corpus.size());report.put("historicalOcrPages",corpus.stream().filter(s->s.captured()!=null).count());
   var required=new HashMap<String,String>();for(var item:dataset.path("sources"))required.put(item.path("id").asText(),key(item.path("sourceFile").asText(),item.path("sourcePage").asInt()));
   var available=corpus.stream().map(Source::key).collect(java.util.stream.Collectors.toSet());
   var missingGold=required.entrySet().stream().filter(item->!available.contains(item.getValue())).map(Map.Entry::getKey).sorted().toList();
   report.put("missingPreparedGoldSources",missingGold);assertTrue(missingGold.isEmpty(),"Gold source coverage missing before any embedding call");
   var texts=new LinkedHashSet<String>();var chunker=new RagTextChunker();
   for(var source:corpus)for(var chunk:chunker.chunks(source.predictedText(),1,1))texts.add(chunk.content());
   for(var query:questions)texts.add(query.path("query").asText());
   client.prime(new ArrayList<>(texts));report.put("realPrimeInputs",texts.size());
   db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
   var permissions=mock(PermissionService.class);when(permissions.resolveActiveUser(1)).thenAnswer(call->user());
   var auth=new RagAuthorizationService(permissions,db);var files=new RagFileStore(temp.resolve("private").toString());
   var knowledge=new NativeRagService(db,new RagDocumentParser(),client,json,auth,files,new RagSchemaMigrator(db),new RagIndexVersionRepository(db));knowledge.initialize();UserContext.set(user());
   var byImage=new HashMap<String,CapturedOcr>();for(var source:corpus)if(source.captured()!=null)byImage.put(source.imageSha(),source.captured());
   String frozenSha=frozen.path("sha256").asText();
   OcrProvider recorded=new OcrProvider(){public String fingerprint(){return "k03-historical-ocr-replay-"+frozenSha;}
    public OcrResult recognize(OcrRequest request){try{var item=byImage.get(sha(request.image()));if(item==null)throw new ModelInvocationException("OCR_NOT_CAPTURED");return new OcrResult(item.raw(),item.normalized(),null,"historical-deepseek-ocr",0,true);}catch(ModelInvocationException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}}};
   var ingestion=new RagIngestionService(db,auth,files,new IngestionJobRepository(db),new RagPageExtractor(),new PageAnalysisService(),recorded,new RagTextChunker(),new RagIndexVersionRepository(db),client,json);
   knowledge.configureIngestion(ingestion);String kb=data(knowledge.createKnowledgeBase("K03 frozen course acceptance","isolated real embeddings")).get("id").toString();
   var documentSources=new LinkedHashMap<String,String>();var failed=new ArrayList<String>();
   for(var source:corpus) {
    var upload=data(knowledge.uploadFile(kb,source.pdf(),source.key()+".pdf"));String id=upload.get("documentId").toString();assertTrue(ingestion.runNext("k03-acceptance"));
    if(db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,id)>0)documentSources.put(id,source.key());else failed.add(source.key());
   }
   report.put("failedSourceCopies",failed);report.put("indexedDocuments",documentSources.size());report.put("indexedChunks",db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk",Integer.class));
   var dimensions=db.queryForList("SELECT DISTINCT dimensions FROM native_rag_index_version WHERE state='HYBRID_READY'",Integer.class);
   assertEquals(List.of(4096),dimensions);report.put("persistedDimensions",dimensions);
   var gold=new HashMap<String,String>();for(var s:dataset.path("sources"))gold.put(s.path("id").asText(),key(s.path("sourceFile").asText(),s.path("sourcePage").asInt()));
   assertTrue(documentSources.values().containsAll(gold.values()),"An expected gold source failed ingestion; see private/cache provenance");
   // Restart uses stored index/vector state. It must not re-embed or simulate a fresh index.
   knowledge=new NativeRagService(db,new RagDocumentParser(),client,json,auth,files,new RagSchemaMigrator(db),new RagIndexVersionRepository(db));knowledge.initialize();
   var keyword=new NativeRagService(db,new RagDocumentParser(),new RagEmbeddingClient(false,"","","",json),json,auth,files,new RagSchemaMigrator(db),new RagIndexVersionRepository(db));keyword.initialize();
   var identityDiagnostic=json.readTree(Files.readString(output.resolve("batch-single-consistency.json")));
   report.put("priorVectorIdentityDiagnostic",identityDiagnostic);
   report.put("initialStabilityAssumption", "cosine>0.999 was an uncalibrated development assumption, withdrawn after repeat-single drift; not a provider contract or retrieval accuracy criterion");
   assertTrue(identityDiagnostic.path("identityObservedCorrectAtAllEightPositions").asBoolean(),"Observed identity diagnostic is incomplete");
   if(run.equals("tuning")) {
    var grid=new ArrayList<Map<String,Object>>();var scores=new ArrayList<K03TuningSupport.Score>();
    for(var k:plan.path("grid").path("rrfK"))for(var c:plan.path("grid").path("candidateLimit")) {
     K03TuningSupport.configure(knowledge,client,json,k.asInt(),c.asInt());
     K03TuningSupport.configure(keyword,new RagEmbeddingClient(false,"","","",json),json,k.asInt(),c.asInt());
     int hits=0,top1=0,bmHits=0;double mrr=0,bmMrr=0;var observations=new ArrayList<Map<String,Object>>();
     for(var q:questions) {
      var hybrid=search(knowledge,kb,q.path("query").asText());var bm25=search(keyword,kb,q.path("query").asText());assertEquals("HYBRID",hybrid.actualMode());
      Set<String> expected=new HashSet<>();for(var id:q.path("goldSourceIds"))expected.add(gold.get(id.asText()));
      int hr=firstRank(hybrid,expected,documentSources),br=firstRank(bm25,expected,documentSources);
      if(hr>0){hits++;mrr+=1.0/hr;if(hr==1)top1++;}if(br>0){bmHits++;bmMrr+=1.0/br;}
      observations.add(Map.of("id",q.path("id").asText(),"hybridRank",hr,"bm25Rank",br));
     }
     var score=new K03TuningSupport.Score(k.asInt(),c.asInt(),hits,top1,mrr/questions.size());scores.add(score);
     var cell=new LinkedHashMap<String,Object>();cell.put("parametersInjected",true);cell.put("score",score);cell.put("questionCount",questions.size());cell.put("bm25Top5Hits",bmHits);cell.put("bm25MrrAt5",bmMrr/questions.size());cell.put("observations",observations);grid.add(cell);
    }
    report.put("grid",grid);report.put("selected",K03TuningSupport.select(scores));report.put("scoredQuestionIds",questions.findValuesAsText("id"));report.put("status","PASS");return;
   }
   int chosenK=60,chosenC=100;
   if(run.equals("followup")) {
    var tuning=json.readTree(Files.readString(output.resolve("tuning-report.json")));assertEquals("PASS",tuning.path("status").asText());assertEquals(plan.path("originalDatasetSha256").asText(),tuning.path("datasetSha256").asText());
    assertEquals(report.get("tuningPlanSha256"),tuning.path("tuningPlanSha256").asText());
    var selected=tuning.path("selected");chosenK=selected.path("rrfK").asInt();chosenC=selected.path("candidateLimit").asInt();
    assertTrue(chosenK>0&&chosenC>0,"Frozen tuning selection missing");
   }
   report.put("parameters",Map.of("rrfK",chosenK,"candidateLimit",chosenC,"explicitlyInjected",true));
   K03TuningSupport.configure(knowledge,client,json,chosenK,chosenC);
   K03TuningSupport.configure(keyword,new RagEmbeddingClient(false,"","","",json),json,chosenK,chosenC);
   int hybridHits=0,lexicalHits=0,answerable=0,noAnswerReturned=0,hybridTop1=0,bm25Top1=0,hybridOnly=0,bm25Only=0;double hybridMrr=0,bm25Mrr=0;
   for(var q:questions) {
    var hybrid=search(knowledge,kb,q.path("query").asText());var bm25=search(keyword,kb,q.path("query").asText());
    assertEquals("HYBRID",json.valueToTree(hybrid).path("actualMode").asText(),"Real query unexpectedly degraded");
    Set<String> expected=new HashSet<>();for(var id:q.path("goldSourceIds"))expected.add(gold.get(id.asText()));
    boolean hh=hybrid.hits().stream().anyMatch(hit->expected.contains(documentSources.get(hit.documentId()))),bh=bm25.hits().stream().anyMatch(hit->expected.contains(documentSources.get(hit.documentId())));
    var row=new LinkedHashMap<String,Object>();row.put("id",q.path("id").asText());row.put("split",q.path("split").asText());row.put("answerable",q.path("answerable").asBoolean());
    row.put("hybridKnownSourceTop5",hh);row.put("bm25KnownSourceTop5",bh);row.put("hybridSources",hybrid.hits().stream().map(hit->documentSources.get(hit.documentId())).toList());row.put("bm25Sources",bm25.hits().stream().map(hit->documentSources.get(hit.documentId())).toList());results.add(row);
    if(q.path("split").asText().equals("acceptance")){if(q.path("answerable").asBoolean()){
     answerable++;if(hh)hybridHits++;if(bh)lexicalHits++;if(hh&&!bh)hybridOnly++;if(bh&&!hh)bm25Only++;
     int hr=firstRank(hybrid,expected,documentSources),br=firstRank(bm25,expected,documentSources);
     if(hr>0){hybridMrr+=1.0/hr;if(hr==1)hybridTop1++;}if(br>0){bm25Mrr+=1.0/br;if(br==1)bm25Top1++;}
    }else if(!hybrid.hits().isEmpty())noAnswerReturned++;}
   }
   report.put("acceptanceAnswerable",answerable);report.put("hybridTop5Hits",hybridHits);report.put("bm25Top5Hits",lexicalHits);report.put("hybridTop5Rate",hybridHits/(double)answerable);report.put("bm25Top5Rate",lexicalHits/(double)answerable);report.put("noAnswerQueriesWithCandidates",noAnswerReturned);
   report.put("hybridTop1Hits",hybridTop1);report.put("bm25Top1Hits",bm25Top1);report.put("hybridMrrAt5",hybridMrr/answerable);report.put("bm25MrrAt5",bm25Mrr/answerable);report.put("hybridOnlyTop5Hits",hybridOnly);report.put("bm25OnlyTop5Hits",bm25Only);
   // At least one actual query enters production RagEmbeddingClient HTTP, with no query cache.
   var direct=new NativeRagService(db,new RagDocumentParser(),real,json,auth,files,new RagSchemaMigrator(db),new RagIndexVersionRepository(db));direct.initialize();K03TuningSupport.configure(direct,real,json,chosenK,chosenC);
   assertEquals("HYBRID",json.valueToTree(search(direct,kb,questions.get(0).path("query").asText())).path("actualMode").asText());report.put("directProductionHttpQueryPassed",true);
   var unavailable=mock(RagEmbeddingClient.class);when(unavailable.isConfigured()).thenReturn(true);when(unavailable.isRequested()).thenReturn(true);when(unavailable.fingerprint()).thenReturn(real.fingerprint());when(unavailable.modelName()).thenReturn(real.modelName());when(unavailable.embed(anyList())).thenThrow(new IllegalStateException("controlled provider failure"));
   var broken=new NativeRagService(db,new RagDocumentParser(),unavailable,json,auth,files,new RagSchemaMigrator(db),new RagIndexVersionRepository(db));broken.initialize();K03TuningSupport.configure(broken,unavailable,json,chosenK,chosenC);
   var fallback=json.valueToTree(search(broken,kb,"机器学习模型"));assertEquals("KEYWORD",fallback.path("actualMode").asText());assertFalse(fallback.path("degradedReason").asText().isBlank());report.put("providerFailureDegradedExplicitly",true);
   var stored=db.queryForList("SELECT id,vector_json,vector_model FROM native_rag_chunk");
   db.update("UPDATE native_rag_chunk SET vector_json='[1,0]'");
   assertEquals("KEYWORD",json.valueToTree(search(knowledge,kb,"机器学习模型")).path("actualMode").asText());report.put("wrongDimensionsIsolated",true);
   for(var row:stored)db.update("UPDATE native_rag_chunk SET vector_json=?,vector_model=? WHERE id=?",row.get("vector_json"),row.get("vector_model"),row.get("id"));
   db.update("UPDATE native_rag_chunk SET vector_model='other-model-fixture'");
   assertEquals("KEYWORD",json.valueToTree(search(knowledge,kb,"机器学习模型")).path("actualMode").asText());report.put("wrongModelIsolated",true);
   report.put("realEmbeddingFacadeCalls",client.realCalls);report.put("realEmbeddingInputs",client.realInputs);report.put("reusedExactRealVectors",client.reused);report.put("persistedRestartPassed",true);
   assertEquals(40,answerable);assertTrue(hybridHits/(double)answerable>=.85,"Known-source Top5 below frozen target; do not tune on acceptance results");
   report.put("status","PASS");
  } finally {
   report.put("elapsedMs",(System.nanoTime()-started)/1_000_000);if(client!=null){report.put("realEmbeddingFacadeCalls",client.realCalls);report.put("realEmbeddingInputs",client.realInputs);report.put("reusedExactRealVectors",client.reused);report.put("persistedCachedVectorsLoaded",client.loadedVectors);report.put("persistedCachedVectorReuses",client.persistedReused);}
   Files.writeString(output.resolve(outputName),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
   UserContext.clear();if(db!=null)db.execute("DROP ALL OBJECTS");
  }
 }

 List<Source> corpus(Path root,JsonNode dataset,List<Map<String,Object>> skipped) throws Exception {
  var captured=new HashMap<String,CapturedOcr>();var old=json.readTree(Files.readString(root.resolve("docs/competition/development/K02-verification-20261025/course-materials-final.json")));
  var scan=json.readTree(Files.readString(Path.of("target/k02-live/scan-page-results-private.json")));
  for(var source:old.path("scanManifest")){int unit=source.path("copyUnit").asInt();var item=scan.get(unit-1);captured.put(key(source.path("sourceName").asText(),source.path("sourcePage").asInt()),new CapturedOcr(item.path("ocrRawText").asText(),item.path("ocrText").asText()));}
  var formulas=json.readTree(Files.readString(Path.of("target/k02-formulas/formula-live-private.json")));
  List<Path> paths;try(var originals=Files.list(Path.of("D:/PPT/人工智能"))){paths=originals.filter(p->p.toString().endsWith(".pdf")).sorted().toList();}
  for(var item:formulas)if(!item.path("scanned").asBoolean()){
   String[] tag=item.path("tag").asText().split("-p");int chapter=Integer.parseInt(tag[0].substring(2)),page=Integer.parseInt(tag[1]);
   Path path=paths.stream().filter(p->p.getFileName().toString().contains("第"+chapter+"章")).findFirst().orElseThrow();String raw=item.path("rawOcr").asText();
   captured.put(key(path.getFileName().toString(),page),new CapturedOcr(raw,new OcrResultNormalizer().normalize(raw).text()));
  }
  Path supplemental=Path.of("target/k03-retrieval/supplemental-ocr-cache-private.json");
  if(Files.exists(supplemental))for(var item:json.readTree(Files.readString(supplemental))) {
   String name=item.path("sourceFile").asText();int originalPage=item.path("sourcePage").asInt();
   Path sourcePath=paths.stream().filter(p->p.getFileName().toString().equals(name)).findFirst().orElseThrow();
   byte[] original=Files.readAllBytes(sourcePath);assertEquals(item.path("sourceSha256").asText(),sha(original),"Supplemental OCR original changed");
   try(var source=new RagPageExtractor().open(original,name)){assertEquals(item.path("imageSha256").asText(),sha(source.renderPng(originalPage)),"Supplemental OCR image changed");}
   assertEquals("stop",item.path("finishReason").asText(),"Supplemental OCR is incomplete");
   assertEquals("deepseek-ai/DeepSeek-OCR",item.path("model").asText());
   assertEquals("<image>\n<|grounding|>Convert the document to markdown.",item.path("prompt").asText());
   String raw=item.path("raw").asText();captured.put(key(name,originalPage),new CapturedOcr(raw,new OcrResultNormalizer().normalize(raw).text()));
  }
  var result=new ArrayList<Source>();var analysis=new PageAnalysisService();var normalizer=new OcrResultNormalizer();
  for(Path path:paths) {
   byte[] original=Files.readAllBytes(path);String name=path.getFileName().toString(),hash=sha(original);
   var known=old.path("scanManifest");for(var item:known)if(item.path("sourceName").asText().equals(name))assertEquals(hash,item.path("sourceSha256").asText(),"Original source drifted since captured OCR");
   try(var source=new RagPageExtractor().open(original,name);var document=Loader.loadPDF(original)) {
    for(int page=1;page<=source.pageCount();page++) {
     var extracted=source.page(page);String tag=key(name,page);var replay=captured.get(tag);var state=analysis.analyze(extracted.text(),extracted.hasImages(),extracted.imageCoverage(),false);
     boolean requires=state.needsOcr()||extracted.formulaHint();String chosen=extracted.text();
     if(requires && replay==null){skipped.add(Map.of("sourceFile",name,"sourcePage",page,"reason","OCR_NOT_IN_CAPTURED_SAMPLE"));continue;}
     if(requires){chosen=normalizer.merge(extracted.text(),replay.normalized());if(FormulaText.analyze(chosen).blocks().isEmpty()&&(extracted.formulaHint()||FormulaText.suspectMath(replay.normalized()))){skipped.add(Map.of("sourceFile",name,"sourcePage",page,"reason","CAPTURED_FORMULA_NOT_STRUCTURED"));continue;}}
     if(chosen.length()<80 || !FormulaText.analyze(chosen).valid()){skipped.add(Map.of("sourceFile",name,"sourcePage",page,"reason","INSUFFICIENT_OR_INVALID_TEXT"));continue;}
     byte[] bytes;try(var copy=new PDDocument();var output=new ByteArrayOutputStream()){copy.importPage(document.getPage(page-1));copy.save(output);bytes=output.toByteArray();}
     String pixels=null;if(requires)try(var copy=new RagPageExtractor().open(bytes,tag+".pdf")){pixels=sha(copy.renderPng(1));}
     result.add(new Source(tag,name,page,hash,bytes,chosen,pixels,requires?replay:null));
    }
   }
   assertEquals(hash,sha(Files.readAllBytes(path)),"Original source changed");
  }
  Files.writeString(Path.of("target/k03-retrieval/corpus-provenance.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result.stream().map(s->Map.of("key",s.key(),"sourceFile",s.file(),"originalPage",s.originalPage(),"sourceSha256",s.sourceSha(),"copyPage",1,"historicalOcr",s.captured()!=null,"chosenCharacters",s.predictedText().length())).toList()));
  return result;
 }
 static int firstRank(RagSearchResponse response,Set<String> expected,Map<String,String> documentSources) {
  for(int i=0;i<response.hits().size();i++)if(expected.contains(documentSources.get(response.hits().get(i).documentId())))return i+1;return 0;
 }
 static String key(String file,int page){return file.replaceAll("\\.pdf$","")+"-p"+page;}
 static AuthUtils.LoginUser user(){var user=new AuthUtils.LoginUser();user.setUserId(1);user.setRoleCode("teacher");return user;}
 static String setting(String... names){for(var n:names){String value=System.getenv(n);if(value!=null&&!value.isBlank())return value;}return "";}
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 @SuppressWarnings("unchecked") static Map<String,Object> data(Result result){assertEquals("200",result.getCode(),result.getMsg());return (Map<String,Object>)result.getData();}
 static RagSearchResponse search(NativeRagService service,String kb,String query){var result=service.search(new RagSearchRequest(List.of(kb),query,5));assertEquals("200",result.getCode(),result.getMsg());return (RagSearchResponse)result.getData();}
}
