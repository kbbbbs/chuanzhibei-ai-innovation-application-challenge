package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ocr.*;
import com.cupk.aiacademy.rag.visual.*;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="K02_VISUAL_LIVE_CHECK",matches="true")
class K02VisualLiveTest {
 @TempDir Path temp;
 @Test void realCourseFiguresAreStoredAndReadFromTheActualRetrievedSource() throws Exception {
  var fixture=new K02IngestionServiceTest();fixture.temp=temp;fixture.setup();var mapper=new ObjectMapper();
  Path output=Path.of("target/k02-visuals");Files.createDirectories(output);
  var report=new LinkedHashMap<String,Object>();var samples=new ArrayList<Map<String,Object>>();var privateReadings=new ArrayList<Map<String,Object>>();
  report.put("status","FAIL");report.put("samples",samples);report.put("embeddingEnabled",false);
  report.put("scope","Production ingestion, private pixels and actual DeepSeek-OCR figure reader. No live answer-model or accuracy claim.");
  long started=System.nanoTime();RagVisualReader reader=null;
  try {
   var config=new RuntimeModelProperties();config.getOcr().setBaseUrl(setting("OCR_BASE_URL"));config.getOcr().setModel(setting("OCR_MODEL"));
   config.getOcr().setApiKey(setting("OCR_API_KEY","OCR_APIKEY","SILICONFLOW_API_KEY"));config.getOcr().setTimeoutSeconds(60);
   var client=new OcrModelClient(config,mapper);var provider=new OcrModelAdapter(config,client,new OcrResultNormalizer());
   fixture.service=new RagIngestionService(fixture.db,fixture.auth,fixture.files,fixture.jobs,new RagPageExtractor(),new PageAnalysisService(),provider,new RagTextChunker(),new RagIndexVersionRepository(fixture.db),fixture.embeddings,mapper);
   var visuals=new RagVisualService(fixture.db,fixture.auth,mapper);reader=new RagVisualReader(visuals,client,30000);
   List<Path> originals;try(var list=Files.list(Path.of("D:/PPT/人工智能"))){originals=list.filter(p->p.toString().endsWith(".pdf")).toList();}
   for(int[] selected:new int[][]{{4,10},{5,14},{3,23}}) {
    Path source=originals.stream().filter(p->p.getFileName().toString().contains("第"+selected[0]+"章")).findFirst().orElseThrow();
    byte[] original=Files.readAllBytes(source);String hash=sha(original),tag="ch"+selected[0]+"-p"+selected[1];byte[] input;
    try(var pdf=Loader.loadPDF(original);var copy=new PDDocument();var buffer=new ByteArrayOutputStream()){copy.importPage(pdf.getPage(selected[1]-1));copy.save(buffer);input=buffer.toByteArray();}
    var upload=fixture.data(fixture.service.submit(fixture.base,input,tag+".pdf"));String doc=fixture.value(upload,"documentId");
    assertTrue(fixture.service.runNext("visual-live"));
    var page=(Map<?,?>)((List<?>)fixture.service.pages(doc,1).getData()).getFirst();
    var summary=new LinkedHashMap<String,Object>();summary.put("sourceFile",source.getFileName().toString());summary.put("sourceSha256",hash);
    summary.put("sourcePage",selected[1]);summary.put("copyPage",1);summary.put("pageState",page.get("pageStatus"));summary.put("errorCode",page.get("errorCode"));
    var images=(List<RagVisual>)page.get("visuals");summary.put("visualCount",images.size());summary.put("figures",images.stream().filter(v->v.kind().equals("FIGURE")).count());summary.put("issues",page.get("visualIssues"));samples.add(summary);
    assertFalse(images.isEmpty(),"No source visual retained");
    for(var image:images){byte[] png=visuals.imageForActor(image.id(),1);Files.write(output.resolve(tag+"-visual-"+image.ordinal()+".png"),png);}
    var row=fixture.db.queryForMap("SELECT * FROM native_rag_chunk WHERE document_id=? ORDER BY ordinal_number LIMIT 1",doc);
    var basic=new RagSearchHit(row.get("id").toString(),doc,fixture.base,source.getFileName().toString(),row.get("content").toString(),1,1,1,"[1]");
    var hit=new RagSearchHit(basic.chunkId(),doc,fixture.base,basic.title(),basic.content(),1,1,1,"[1]",visuals.forHit(basic,1));
    var reading=reader.read(List.of(hit),1,false);summary.put("readerHasEvidence",!reading.evidence().isBlank());summary.put("readerWarning",reading.warning());
    privateReadings.add(Map.of("tag",tag,"visuals",images,"chosenText",page.get("chosenText"),"evidence",reading.evidence()));
    assertFalse(reading.evidence().isBlank(),"Real figure reading produced no usable observation");
    assertEquals(hash,sha(Files.readAllBytes(source)),"Original source changed");
   }
   report.put("status","PASS");
  } finally {
   report.put("elapsedMs",(System.nanoTime()-started)/1_000_000);
   Files.writeString(output.resolve("visual-live-report.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
   Files.writeString(output.resolve("visual-live-private.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(privateReadings));
   if(reader!=null)reader.shutdown();fixture.cleanup();
  }
 }
 private static String setting(String... names){for(String name:names){String value=System.getenv(name);if(value!=null&&!value.isBlank())return value;}return "";}
 private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
}
