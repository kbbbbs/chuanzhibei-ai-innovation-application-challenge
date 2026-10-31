package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.visual.*;
import com.cupk.aiacademy.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class K02VisualIngestionTest {
    @TempDir Path temp;
    K02IngestionServiceTest fixture;
    @BeforeEach void setup() throws Exception {fixture=new K02IngestionServiceTest();fixture.temp=temp;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}

    @Test void groundedImageIsPrivatePersistedAndAssociatedWithItsOriginalPageAndQuestion() throws Exception {
        String raw="# Network lesson\n1. Read the diagram.\n<|ref|>image<|/ref|><|det|>[[100,100,800,800]]<|/det|>\nFigure 1: input and output.\n$$x=y+1$$";
        when(fixture.ocr.recognize(any())).thenReturn(new com.cupk.aiacademy.rag.ocr.OcrResult(raw,
                new com.cupk.aiacademy.rag.ocr.OcrResultNormalizer().normalize(raw).text(),null,"fixture",1,true));
        var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"network.pdf"));
        String id=fixture.value(upload,"documentId");assertTrue(fixture.service.runNext("visual-test"));
        var pages=(List<?>)fixture.service.pages(id,1).getData();var page=(Map<?,?>)pages.getFirst();
        assertEquals(1,page.get("visualVersion"));var visuals=(List<?>)page.get("visuals");assertEquals(1,visuals.size());
        var visual=(RagVisual)visuals.getFirst();assertEquals("FIGURE",visual.kind());assertEquals("OCR_GROUNDING",visual.origin());
        assertEquals(1,visual.page());assertEquals("1",visual.questionNumber());assertTrue(visual.context().contains("diagram"));
        assertEquals(List.of(100,100,800,800),visual.bbox());
        var questions=(List<?>)page.get("questions");assertNotNull(questions,"Recognizable question groups must be available for teacher review");
        assertEquals("1",((Map<?,?>)questions.getFirst()).get("number"));
        assertTrue(((Map<?,?>)questions.getFirst()).get("text").toString().contains("diagram"));
        assertTrue(((List<?>)((Map<?,?>)questions.getFirst()).get("visualIds")).contains(visual.id()));
        var service=new RagVisualService(fixture.db,fixture.auth,new ObjectMapper());
        byte[] png=service.imageForActor(visual.id(),1);assertEquals((byte)0x89,png[0]);
        assertThrows(RagAccessException.class,()->service.imageForActor(visual.id(),3));
        var hit=new RagSearchHit("fake",id,fixture.base,"network.pdf",page.get("chosenText").toString(),1,1,1,"[1]");
        // Retrieval must resolve an actual active chunk, not trust a caller-supplied page or version.
        assertThrows(RagAccessException.class,()->service.forHit(hit,1));
        String chunk=fixture.db.queryForObject("SELECT id FROM native_rag_chunk WHERE document_id=? LIMIT 1",String.class,id);
        var actual=new RagSearchHit(chunk,id,fixture.base,"network.pdf",page.get("chosenText").toString(),1,1,1,"[1]");
        assertEquals(visual.id(),service.forHit(actual,1).getFirst().id());
        fixture.roles.put(3,"teacher");fixture.login(3);
        assertEquals("403",assertThrows(RagAccessException.class,()->service.imageForActor(visual.id(),3)).code());
        fixture.login(1);
        fixture.db.update("UPDATE native_rag_visual SET image_sha256='corrupted' WHERE id=?",visual.id());
        assertEquals("409",assertThrows(RagAccessException.class,()->service.imageForActor(visual.id(),1)).code());
    }

    @Test void invalidGroundingCannotBecomeAFalsePreciseCrop() throws Exception {
        String raw="Question image <|ref|>image<|/ref|><|det|>[[-5,10,1000,900]]<|/det|>";
        when(fixture.ocr.recognize(any())).thenReturn(new com.cupk.aiacademy.rag.ocr.OcrResult(raw,"Question image",null,"fixture",1,true));
        var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"invalid-box.pdf"));String id=fixture.value(upload,"documentId");
        fixture.service.runNext("invalid-box");
        var page=(Map<?,?>)((List<?>)fixture.service.pages(id,1).getData()).getFirst();var visual=(RagVisual)((List<?>)page.get("visuals")).getFirst();
        assertEquals("PAGE_FALLBACK",visual.origin());assertTrue(visual.bbox().isEmpty());
        assertFalse(page.get("visualIssues").toString().equals("[]"));
    }

    @Test void missingGroundingUsesExplicitPageFallbackAndReparseDoesNotDestroyPriorVisuals() throws Exception {
        when(fixture.ocr.recognize(any())).thenReturn(new com.cupk.aiacademy.rag.ocr.OcrResult("Lecture image","Lecture image",null,"fixture",1,true));
        var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"diagram.pdf"));String id=fixture.value(upload,"documentId");
        fixture.service.runNext("visual-fallback");
        var page=(Map<?,?>)((List<?>)fixture.service.pages(id,1).getData()).getFirst();var first=(RagVisual)((List<?>)page.get("visuals")).getFirst();
        assertEquals("PAGE",first.kind());assertEquals("PAGE_FALLBACK",first.origin());assertTrue(first.bbox().isEmpty());
        fixture.data(fixture.service.retry(id,List.of(1),false));fixture.service.runNext("visual-repeat");
        var next=(Map<?,?>)((List<?>)fixture.service.pages(id,1).getData()).getFirst();var second=(RagVisual)((List<?>)next.get("visuals")).getFirst();
        assertEquals(2,second.parseRevision());assertNotEquals(first.id(),second.id());
        var service=new RagVisualService(fixture.db,fixture.auth,new ObjectMapper());
        assertArrayEquals(service.imageForActor(first.id(),1),service.imageForActor(second.id(),1));
        new RagSchemaMigrator(fixture.db).migrate();assertEquals(2,fixture.db.queryForObject("SELECT COUNT(*) FROM native_rag_visual WHERE document_id=?",Integer.class,id));
        fixture.db.update("DELETE FROM native_rag_document WHERE id=?",id);
        assertEquals(0,fixture.db.queryForObject("SELECT COUNT(*) FROM native_rag_visual WHERE document_id=?",Integer.class,id));
    }

    @Test void anImageOnlyOcrPageKeepsAnExplicitVisualReferenceInsteadOfDisappearingFromRetrieval() throws Exception {
        String raw="<|ref|>image<|/ref|><|det|>[[100,100,800,800]]<|/det|>";
        when(fixture.ocr.recognize(any())).thenReturn(new com.cupk.aiacademy.rag.ocr.OcrResult(raw,"",null,"fixture",1,true,"OCR_EMPTY_RESULT"));
        var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"neural-network-diagram.pdf"));String id=fixture.value(upload,"documentId");
        fixture.service.runNext("visual-only");
        var checkpoint=fixture.jobs.pages(id,1,1).getFirst();assertEquals("OCR_READY",checkpoint.state());
        assertTrue(checkpoint.chosenText().contains("图像来源信息"));assertTrue(checkpoint.chosenText().contains("非OCR正文"));
        var page=(Map<?,?>)((List<?>)fixture.service.pages(id,1).getData()).getFirst();
        assertEquals(1,((List<?>)page.get("visuals")).size());
        assertTrue(page.get("visualIssues").toString().contains("VISUAL_ONLY_PAGE"));
    }
    @Test void multipleQuestionsKeepTheirOwnVisualsAcrossTextChunks() throws Exception {
        String raw="<|ref|>title<|/ref|><|det|>[[10,0,990,60]]<|/det|>\n公共试卷标题：多题配图与上下文关联测试\n"
                +"<|ref|>text<|/ref|><|det|>[[10,70,990,150]]<|/det|>\n1. 第一题，只能使用第一张图。"+"甲".repeat(1400)+"\n"
                +"<|ref|>image<|/ref|><|det|>[[100,200,450,450]]<|/det|>\n"
                +"<|ref|>text<|/ref|><|det|>[[10,500,990,600]]<|/det|>\n2. 第二题，只能使用第二张图。"+"乙".repeat(1400)+"\n"
                +"<|ref|>image<|/ref|><|det|>[[500,650,900,950]]<|/det|>";
        var normalized=new com.cupk.aiacademy.rag.ocr.OcrResultNormalizer().normalize(raw).text();
        when(fixture.ocr.recognize(any())).thenReturn(new com.cupk.aiacademy.rag.ocr.OcrResult(raw,normalized,null,"fixture",1,true));
        var upload=fixture.data(fixture.service.submit(fixture.base,fixture.pdf(true),"multiple-questions.pdf"));
        String id=fixture.value(upload,"documentId");assertTrue(fixture.service.runNext("multi-question-visuals"));
        var service=new RagVisualService(fixture.db,fixture.auth,new ObjectMapper());
        var chunks=fixture.db.queryForList("SELECT id,content,ordinal_number FROM native_rag_chunk WHERE document_id=? ORDER BY ordinal_number",id);
        var second=chunks.stream().filter(c->c.get("content").toString().contains("乙".repeat(100))
                && !c.get("content").toString().contains("甲")).reduce((a,b)->b).orElseThrow();
        var hit=new RagSearchHit(second.get("id").toString(),id,fixture.base,"multiple-questions.pdf",second.get("content").toString(),1,
                ((Number)second.get("ordinal_number")).intValue(),1,"[1]");
        var associated=service.forHit(hit,1);
        assertEquals(1,associated.size(),"a later question chunk must retain its own image, without the previous question's figure");
        assertEquals("2",associated.getFirst().questionNumber());
        var first=chunks.stream().filter(c->c.get("content").toString().contains("甲".repeat(100))
                && !c.get("content").toString().contains("乙")).reduce((a,b)->b).orElseThrow();
        var firstHit=new RagSearchHit(first.get("id").toString(),id,fixture.base,"multiple-questions.pdf",first.get("content").toString(),1,
                ((Number)first.get("ordinal_number")).intValue(),1,"[1]");
        assertEquals(List.of("1"),service.forHit(firstHit,1).stream().map(RagVisual::questionNumber).toList());
    }
}
