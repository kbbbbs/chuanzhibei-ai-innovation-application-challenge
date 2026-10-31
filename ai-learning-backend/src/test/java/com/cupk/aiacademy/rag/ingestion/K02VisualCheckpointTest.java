package com.cupk.aiacademy.rag.ingestion;
import com.cupk.aiacademy.rag.visual.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class K02VisualCheckpointTest {
 @TempDir Path temp;
 @Test void changedRecognitionWithinRecoveredRevisionReplacesItsDerivedImages() throws Exception {
  var fixture=new K02IngestionServiceTest();fixture.temp=temp;fixture.setup();
  try {
   var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"recover.pdf"));String doc=fixture.value(upload,"documentId");
   fixture.service.runNext("checkpoint");
   byte[] png;try(var source=new RagPageExtractor().open(fixture.pdf(true),"recover.pdf")){png=source.renderPng(1);}
   var service=new RagVisualService(fixture.db,fixture.auth,new ObjectMapper());
   String first="1. old diagram\n<|ref|>image<|/ref|><|det|>[[0,0,500,500]]<|/det|>";
   String second="2. recovered diagram\n<|ref|>image<|/ref|><|det|>[[200,200,900,900]]<|/det|>";
   service.capture(doc,1,1,1,png,first,"1. old diagram");
   String a=((RagVisual)((List<?>)service.pageView(doc,1,1,1).get("visuals")).getFirst()).id();
   service.capture(doc,1,1,1,png,second,"2. recovered diagram");
   var next=(RagVisual)((List<?>)service.pageView(doc,1,1,1).get("visuals")).getFirst();
   assertNotEquals(a,next.id());assertEquals("2",next.questionNumber());assertEquals(List.of(200,200,900,900),next.bbox());
   service.capture(doc,1,1,1,png,second,"2. recovered diagram");
   assertEquals(next.id(),((RagVisual)((List<?>)service.pageView(doc,1,1,1).get("visuals")).getFirst()).id());
   assertEquals(1,fixture.db.queryForObject("SELECT COUNT(*) FROM native_rag_visual WHERE document_id=?",Integer.class,doc));
  } finally {fixture.cleanup();}
 }
}
