package com.cupk.aiacademy.model;

import com.cupk.aiacademy.rag.RagEmbeddingClient;
import com.cupk.aiacademy.rag.RagDocumentParser;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagSearchRequest;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.cupk.aiacademy.rag.ocr.OcrModelClient;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only. Never included as a successful provider check when a call is skipped. */
@EnabledIfEnvironmentVariable(named="K00_LIVE_MODEL_CHECK",matches="true")
class RuntimeModelLiveTest {
    @TempDir Path temp;
    @Test void generationCapabilities() { probe("generation"); }
    @Test void planningCapabilities() { probe("planning"); }
    @Test void embeddingCapabilities() { probe("embedding"); }
    @Test void ocrCapabilities() { probe("ocr"); }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RuntimeModelConfiguration.class,RagEmbeddingClient.class,OcrModelClient.class,RuntimeModelCapabilityService.class)
                .withBean(ObjectMapper.class,ObjectMapper::new);
    }

    private void probe(String role) {
        context().run(context->{
                    var report=context.getBean(RuntimeModelCapabilityService.class).probe(role);
                    String json=new ObjectMapper().writeValueAsString(report);
                    Path output=Path.of("target/k00-live/"+role+".json");
                    Files.createDirectories(output.getParent()); Files.writeString(output,json);
                    if (role.equals("generation")) Files.writeString(output.resolveSibling("status.json"),
                            new ObjectMapper().writeValueAsString(context.getBean(RuntimeModelCapabilityService.class).status()));
                    System.out.println("K00_LIVE_RESULT "+json);
                    assertEquals("PASS",report.status(),
                            "Actual provider check failed; see sanitized target/k00-live/"+role+".json");
                });
    }

    @Test void actualEmbeddingIsPersistedAndUsedInNativeRagRetrieval() {
        context().run(context->{
            var mapper=context.getBean(ObjectMapper.class);
            var source=new DriverManagerDataSource("jdbc:h2:mem:live_rag;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            var jdbc=new JdbcTemplate(source);
            var service=new NativeRagService(jdbc,new RagDocumentParser(),context.getBean(RagEmbeddingClient.class),mapper,temp.toString());
            service.initialize();
            var user=new AuthUtils.LoginUser();user.setUserId(1);user.setRoleCode("teacher");UserContext.set(user);
            try {
                var created=service.createKnowledgeBase("Synthetic API test","Only synthetic test content");
                assertEquals("200",created.getCode());
                String kb=((Map<?,?>)created.getData()).get("id").toString();
                String text="Gradient descent updates parameters along the negative gradient to minimize training loss.";
                var uploaded=service.uploadFile(kb,text.getBytes(StandardCharsets.UTF_8),"synthetic.txt");
                assertEquals("200",uploaded.getCode());
                assertEquals("indexed",((Map<?,?>)uploaded.getData()).get("embedding_status"));
                String vector=jdbc.queryForObject("SELECT vector_json FROM native_rag_chunk",String.class);
                int dimensions=mapper.readTree(vector).size();
                assertEquals(4096,dimensions);
                var searched=service.search(new RagSearchRequest(List.of(kb),"gradient descent minimizes training loss",3));
                assertEquals("200",searched.getCode());
                var result=(RagSearchResponse)searched.getData();
                assertEquals("hybrid",result.mode());
                assertFalse(result.hits().isEmpty());
                assertEquals(text,result.hits().getFirst().content());
                var report=Map.of("status","PASS","dimensions",dimensions,"storedJsonCharacters",vector.length(),
                        "searchMode",result.mode(),"hitCount",result.hits().size(),"sourceMatches",true,"storage","isolated H2 MySQL mode");
                Path output=Path.of("target/k00-live/native-rag.json");
                Files.createDirectories(output.getParent());Files.writeString(output,mapper.writeValueAsString(report));
                System.out.println("K00_LIVE_RAG_RESULT "+mapper.writeValueAsString(report));
            } finally { UserContext.clear(); jdbc.execute("DROP ALL OBJECTS"); }
        });
    }
}
