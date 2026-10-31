package com.cupk.aiacademy.rag.ingestion;
import com.cupk.aiacademy.rag.visual.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class K02VisualHttpTest {
 @TempDir Path temp;
 @Test void privateImageResponseUsesOwnerAuthorizationAndNoStoreHeaders() throws Exception {
  var fixture=new K02IngestionServiceTest();fixture.temp=temp;fixture.setup();
  try {
   var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"private.pdf"));String doc=fixture.value(upload,"documentId");fixture.service.runNext("http");
   var page=(Map<?,?>)((List<?>)fixture.service.pages(doc,1).getData()).getFirst();var image=(RagVisual)((List<?>)page.get("visuals")).getFirst();
   var service=new RagVisualService(fixture.db,fixture.auth,new ObjectMapper());var mvc=MockMvcBuilders.standaloneSetup(new RagVisualController(service,fixture.auth)).build();
   mvc.perform(get(image.imageUrl())).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
       .andExpect(header().string("Cache-Control","private, no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
   fixture.login(2);mvc.perform(get(image.imageUrl())).andExpect(status().isForbidden());
   fixture.login(1);mvc.perform(get("/api/rag/visuals/"+UUID.randomUUID()+"/image")).andExpect(status().isNotFound());
   mvc.perform(get("/api/rag/visuals/not-an-id/image")).andExpect(status().isBadRequest());
  } finally {fixture.cleanup();}
 }
}
