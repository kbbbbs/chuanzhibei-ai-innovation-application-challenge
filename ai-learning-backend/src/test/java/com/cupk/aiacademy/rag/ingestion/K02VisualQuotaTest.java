package com.cupk.aiacademy.rag.ingestion;
import com.cupk.aiacademy.rag.visual.RagVisualService;
import com.cupk.aiacademy.rag.ocr.OcrResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class K02VisualQuotaTest {
 @TempDir Path temp;
 @Test void aPureImagePageWithoutAPersistedImageCannotPublishAMetadataOnlySource() throws Exception {
  var fixture=new K02IngestionServiceTest();fixture.temp=temp;fixture.setup();
  try {
   String raw="<|ref|>image<|/ref|><|det|>[[100,100,900,900]]<|/det|>";
   when(fixture.ocr.recognize(any())).thenReturn(new OcrResult(raw,"",null,"fixture",1,true,"OCR_EMPTY_RESULT"));
   var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"visual-quota.pdf"));String doc=fixture.value(upload,"documentId");
   // Synthetic quota counter: no large image allocation or external model call is needed.
   fixture.db.update("INSERT INTO native_rag_visual(id,document_id,document_version,parse_revision,page_no,ordinal_number,metadata_json,image_png,image_sha256,byte_size) VALUES(?,?,1,1,99,1,'{}',?,'quota-fixture',?)",
      UUID.randomUUID().toString(),doc,new byte[]{1},RagVisualService.MAX_REVISION_BYTES);
   fixture.service.runNext("quota");
   var page=fixture.jobs.pages(doc,1,1).getFirst();assertEquals("FAILED",page.state());assertEquals("",page.chosenText());
   assertEquals("VISUAL_IMAGE_UNAVAILABLE",page.errorCode());
   assertEquals(0,fixture.db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,doc));
  } finally {fixture.cleanup();}
 }
}
