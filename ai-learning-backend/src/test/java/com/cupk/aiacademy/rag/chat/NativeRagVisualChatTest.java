package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.visual.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.MimeTypeUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeRagVisualChatTest {
    NativeRagService knowledge;OpenAiChatModel model;NativeRagChatService chat;JdbcTemplate db;RagVisualReader reader;
    final String base="7ecbd8ec-c48c-472d-8cae-16c4a8382678";
    RagSearchHit hit;
    @BeforeEach void setup() {
        knowledge=mock(NativeRagService.class);model=mock(OpenAiChatModel.class);reader=mock(RagVisualReader.class);
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        chat=new NativeRagChatService(knowledge,db,new ObjectMapper(),model,5);chat.initialize();
        var user=new AuthUtils.LoginUser();user.setUserId(2);user.setRoleCode("teacher");UserContext.set(user);
        var visual=mock(RagVisual.class);when(visual.id()).thenReturn("visual-1");
        hit=new RagSearchHit("chunk-1","document-1",base,"课件.pdf","卷积网络结构",3,1,1,"[1]",List.of(visual));
        when(knowledge.searchForOwner(anyList(),anyString(),eq(2),anyInt())).thenReturn(Result.success(new RagSearchResponse(List.of(hit),"bm25",null)));
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("输入经过卷积层。[1]")))));
    }
    @AfterEach void cleanup(){chat.shutdown();UserContext.clear();}

    @Test void figureOcrObservationsArePassedWithSourcesAndCitationsStayLinked() {
        chat.configureVisuals(reader,false);
        when(reader.read(anyList(),eq(2),eq(false))).thenReturn(new RagVisualReader.Reading("[1] OCR图像观察：输入箭头连接卷积层",List.of(),"图像观察待核验"));
        var result=chat.chat(new RagChatRequest(List.of(base),"结构图的箭头连接哪里？",null));
        var prompt=ArgumentCaptor.forClass(Prompt.class);verify(model).call(prompt.capture());
        assertTrue(prompt.getValue().getContents().contains("输入箭头连接卷积层"));
        assertEquals(1,result.citations().getFirst().visuals().size());
        assertTrue(result.warning().contains("图像观察待核验"));
        var stored=chat.messages(result.sessionId()).get(1);assertTrue(stored.get("warning").toString().contains("图像观察待核验"));
    }

    @Test void explicitImageModeAddsPrivatePngMediaButNeverSerializesItIntoHistoryEvidence() {
        chat.configureVisuals(reader,true);
        byte[] bytes="PRIVATE_PNG_BYTES_MUST_NOT_ENTER_HISTORY".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(reader.read(anyList(),eq(2),eq(true))).thenReturn(new RagVisualReader.Reading("[1]附图1",List.of(new Media(MimeTypeUtils.IMAGE_PNG,new ByteArrayResource(bytes))),null));
        var result=chat.chat(new RagChatRequest(List.of(base),"看图回答",null));
        var prompt=ArgumentCaptor.forClass(Prompt.class);verify(model).call(prompt.capture());
        var last=(UserMessage)prompt.getValue().getInstructions().getLast();assertEquals(1,last.getMedia().size());
        assertArrayEquals(bytes,last.getMedia().getFirst().getDataAsByteArray());
        var evidence=db.queryForObject("SELECT evidence_json FROM t_native_rag_message WHERE role='assistant'",String.class);
        assertFalse(evidence.contains(Base64.getEncoder().encodeToString(bytes)));assertFalse(evidence.contains("PRIVATE_PNG_BYTES"));
        assertEquals(1,result.citations().getFirst().visuals().size());
    }

    @Test void absenceOfImageReaderIsExplicitAndDoesNotPretendTheDiagramWasSeen() {
        var result=chat.chat(new RagChatRequest(List.of(base),"看图回答",null));
        assertNotNull(result.warning());assertTrue(result.warning().contains("图片尚未读取"));
        var prompt=ArgumentCaptor.forClass(Prompt.class);verify(model).call(prompt.capture());
        assertTrue(prompt.getValue().getContents().contains("不得声称已读取图片"));
    }

    @Test void streamingAlsoReadsImagesAndPersistsTheSameSourceAndWarning() throws Exception {
        chat.configureVisuals(reader,false);
        when(reader.read(anyList(),eq(2),eq(false))).thenReturn(new RagVisualReader.Reading("[1] OCR图像观察：卷积层之后是池化层",List.of(),"图像解读需核验"));
        when(model.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("卷积层之后是池化层。[1]"))))));
        chat.stream(new RagChatRequest(List.of(base),"图中下一层是什么？",null));
        long until=System.nanoTime()+java.time.Duration.ofSeconds(3).toNanos();
        while(db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message WHERE role='assistant'",Integer.class)==0 && System.nanoTime()<until)Thread.sleep(10);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message WHERE role='assistant'",Integer.class));
        var prompt=ArgumentCaptor.forClass(Prompt.class);verify(model).stream(prompt.capture());
        assertTrue(prompt.getValue().getContents().contains("卷积层之后是池化层"));
        String json=db.queryForObject("SELECT evidence_json FROM t_native_rag_message WHERE role='assistant'",String.class);
        assertTrue(json.contains("图像解读需核验"));assertTrue(json.contains("visual-1"));
    }
}
