package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.model.ModelInvocationException;
import com.cupk.aiacademy.model.ModelConfigurationException;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ocr.*;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class K02IngestionServiceTest {
    @TempDir Path temp;
    JdbcTemplate db;RagIngestionService service;RagAuthorizationService auth;RagFileStore files;
    IngestionJobRepository jobs;OcrProvider ocr;RagEmbeddingClient embeddings;
    PermissionService permissions;Map<Integer,String> roles=new HashMap<>();String base;
    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        new RagSchemaMigrator(db).migrate();
        roles.put(1,"teacher");roles.put(2,"student");
        permissions=mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(call->{int id=call.getArgument(0);
            if(!roles.containsKey(id))throw new ForbiddenException("disabled");
            var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(roles.get(id));return user;});
        auth=new RagAuthorizationService(permissions,db);
        files=new RagFileStore(temp.resolve("private").toString());files.initialize();
        jobs=new IngestionJobRepository(db);ocr=mock(OcrProvider.class);when(ocr.fingerprint()).thenReturn("ocr-fixture-v1");
        when(ocr.recognize(any())).thenReturn(new OcrResult("<|ref|>text<|/ref|>Scanned gradient descent source.","Scanned gradient descent source.",null,"ocr-fixture",1,true));
        embeddings=mock(RagEmbeddingClient.class);when(embeddings.fingerprint()).thenReturn("embedding-fixture");
        service=new RagIngestionService(db,auth,files,jobs,new RagPageExtractor(),new PageAnalysisService(),ocr,
                new RagTextChunker(),new RagIndexVersionRepository(db),embeddings,new ObjectMapper());
        base=UUID.randomUUID().toString();db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,1,'K02','')",base);
        login(1);
    }
    @AfterEach void cleanup(){UserContext.clear();db.execute("DROP ALL OBJECTS");}
    void login(int id){var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(roles.get(id));UserContext.set(user);}
    @SuppressWarnings("unchecked") Map<String,Object> data(Result result){assertEquals("200",result.getCode(),result.getMsg());return (Map<String,Object>)result.getData();}
    String value(Map<String,Object> row,String key){return row.get(key).toString();}
    Map<String,Object> doc(String id){return db.queryForMap("SELECT * FROM native_rag_document WHERE id=?",id);}

    @Test void mathFontWithoutReadableOperatorsStillRequestsDocumentRecognition() throws Exception {
        byte[] input;
        try(var pdf=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage();pdf.addPage(page);try(var content=new PDPageContentStream(pdf,page)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.SYMBOL),24);content.newLineAtOffset(50,600);
                content.showText("α");content.endText();
            }
            pdf.save(bytes);input=bytes.toByteArray();
        }
        when(ocr.recognize(any())).thenReturn(new OcrResult("\\(\\alpha\\)","\\(\\alpha\\)",null,"fixture",1,true));
        var upload=data(service.submit(base,input,"math-font.pdf"));service.runNext("formula-font");
        assertEquals("OCR_READY",jobs.pages(value(upload,"documentId"),1,1).getFirst().state());
        verify(ocr).recognize(argThat(OcrRequest::documentMode));
    }

    @Test void truncatedImagePassCanRecoverThroughStructuredDocumentRecognition() throws Exception {
        byte[] input;
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(pdf(true));var bytes=new ByteArrayOutputStream()) {
            try(var content=new PDPageContentStream(pdf,pdf.getPage(0),PDPageContentStream.AppendMode.APPEND,true)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),16);content.newLineAtOffset(50,730);
                content.showText("Lecture diagram");content.endText();
            }
            pdf.save(bytes);input=bytes.toByteArray();
        }
        when(ocr.recognize(any())).thenAnswer(call->{OcrRequest request=call.getArgument(0);
            return request.documentMode()?new OcrResult("\\[\\frac{x}{y}\\]","\\[\\frac{x}{y}\\]",null,"fixture",1,true):
                    new OcrResult("partial","partial",null,"fixture",1,true,"OCR_OUTPUT_TRUNCATED");});
        var upload=data(service.submit(base,input,"formula-image.pdf"));service.runNext("formula-fallback");
        var page=jobs.pages(value(upload,"documentId"),1,1).getFirst();
        assertEquals("OCR_READY",page.state());assertTrue(page.chosenText().contains("\\frac"));
        verify(ocr,times(2)).recognize(any());assertTrue(page.locationJson().contains("initialOcrRawText"));
    }

    @Test void formulaMetadataIsPersistedVersionedAndExposedWithoutFakeCoordinates() throws Exception {
        String content="均方误差\n$$L=\\frac{1}{n}\\sum_i(y_i-\\hat y_i)^2$$\n其中 n 是样本数。";
        var uploaded=data(service.submit(base,content.getBytes(StandardCharsets.UTF_8),"loss.md"));
        String id=value(uploaded,"documentId");service.runNext("formula-worker");
        assertEquals("READY",doc(id).get("processing_state"));verifyNoInteractions(ocr);
        String stored=db.queryForObject("SELECT formula_json FROM native_rag_page WHERE document_id=?",String.class,id);
        var metadata=new ObjectMapper().readTree(stored);
        assertEquals(1,metadata.path("version").asInt());assertEquals(1,metadata.path("blocks").size());
        assertTrue(metadata.path("blocks").get(0).path("latex").asText().contains("\\frac"));
        assertFalse(metadata.path("blocks").get(0).has("bbox"));
        var response=(java.util.List<?>)service.pages(id,1).getData();
        var page=(Map<?,?>)response.getFirst();assertEquals(1,((java.util.List<?>)page.get("formulas")).size());
        assertEquals(true,page.get("needsReview"));assertEquals("TEXT",page.get("formulaSource"));
        data(service.retry(id,List.of(1),false));service.runNext("formula-retry");
        new RagSchemaMigrator(db).migrate();
        assertEquals(stored,db.queryForObject("SELECT formula_json FROM native_rag_page WHERE document_id=? AND parse_revision=1",String.class,id));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM native_rag_page WHERE document_id=?",Integer.class,id));
    }

    @Test void invalidFormulaRemainsReviewableButIsExcludedFromRetrieval() throws Exception {
        String broken="Loss $$\\frac{a}{b";
        when(ocr.recognize(any())).thenReturn(new OcrResult(broken,broken,null,"ocr-fixture",1,true));
        var uploaded=data(service.submit(base,pdf(true),"formula-scan.pdf"));String id=value(uploaded,"documentId");
        service.runNext("formula-worker");var page=jobs.pages(id,1,1).getFirst();
        assertEquals("FAILED",page.state());assertEquals("FORMULA_UNCLOSED",page.errorCode());
        assertEquals(broken,page.ocrRawText());assertEquals(broken,page.ocrText());assertEquals("",page.chosenText());
        assertNull(page.ocrFingerprint());assertNull(doc(id).get("active_index_version"));
    }

    @Test void uploadReturnsQueuedJobAndTextPositionsBeforeBackgroundProcessing() {
        var uploaded=data(service.submit(base,"Gradient descent minimizes training loss.".getBytes(StandardCharsets.UTF_8),"notes.md"));
        String document=value(uploaded,"documentId"),job=value(uploaded,"jobId");
        assertEquals("QUEUED",jobs.get(job).stage());assertNull(doc(document).get("active_index_version"));verifyNoInteractions(ocr);
        assertTrue(service.runNext("test-worker"));assertEquals("COMPLETED",jobs.get(job).stage());
        assertEquals("READY",doc(document).get("processing_state"));assertNotNull(doc(document).get("active_index_version"));
        assertEquals(0,((Number)doc(document).get("page_count")).intValue());
        var chunk=db.queryForMap("SELECT * FROM native_rag_chunk WHERE document_id=?",document);
        assertNull(chunk.get("page_number"));assertEquals(0,((Number)chunk.get("char_start")).intValue());
        assertTrue(((Number)chunk.get("char_end")).intValue()>0);assertFalse(service.runNext("test-worker"));
        verifyNoInteractions(ocr);
    }

    @Test void mixedPdfPersistsNativeOcrAndBlankPagesWithoutIndexingBlankMessages() throws Exception {
        var uploaded=data(service.submit(base,pdf(false),"mixed.pdf"));String document=value(uploaded,"documentId");
        service.runNext("worker");
        var pages=jobs.pages(document,1,1);assertEquals(3,pages.size());
        assertEquals("TEXT_READY",pages.get(0).state());assertEquals("OCR_READY",pages.get(1).state());assertEquals("BLANK",pages.get(2).state());
        assertNull(pages.get(1).confidence());assertTrue(pages.get(1).ocrRawText().contains("<|ref|>"));
        assertFalse(pages.get(1).chosenText().contains("<|ref|>"));
        assertEquals("READY",doc(document).get("processing_state"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE page_number=3",Integer.class));
        verify(ocr,times(1)).recognize(argThat(request->request.pageNumber()==2));
    }

    @Test void failedPageIsPartialAndSelectedRetryReusesGoodPagesWithoutDuplicateChunks() throws Exception {
        when(ocr.recognize(any())).thenThrow(new ModelInvocationException("MODEL_HTTP_503"));
        var uploaded=data(service.submit(base,pdf(false),"partial.pdf"));String document=value(uploaded,"documentId");service.runNext("worker");
        assertEquals("PARTIAL",doc(document).get("processing_state"));String first=doc(document).get("active_index_version").toString();
        assertEquals("FAILED",jobs.pages(document,1,1).get(1).state());
        doReturn(new OcrResult("Recovered OCR source.","Recovered OCR source.",null,"ocr-fixture",1,true)).when(ocr).recognize(any());
        var retried=data(service.retry(document,List.of(2),true));assertEquals(first,doc(document).get("active_index_version"));
        service.runNext("worker");assertEquals("COMPLETED",jobs.get(value(retried,"jobId")).stage());
        assertEquals("READY",doc(document).get("processing_state"));assertNotEquals(first,doc(document).get("active_index_version"));
        assertEquals(3,jobs.pages(document,1,2).size());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE index_version=?",Integer.class,doc(document).get("active_index_version")));
        assertEquals(2,db.queryForObject("SELECT COUNT(DISTINCT ordinal_number) FROM native_rag_chunk WHERE index_version=?",Integer.class,doc(document).get("active_index_version")));
    }

    @Test void selectedRetryPreservesUnselectedFailedPagesWithoutAnotherOcrCall() throws Exception {
        byte[] original;
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf(false));
            var extraScan=org.apache.pdfbox.Loader.loadPDF(pdf(true));var output=new ByteArrayOutputStream()) {
            document.removePage(2);document.importPage(extraScan.getPage(0));document.save(output);original=output.toByteArray();
        }
        when(ocr.recognize(any())).thenThrow(new ModelInvocationException("MODEL_HTTP_503"));
        var uploaded=data(service.submit(base,original,"two-failed-scans.pdf"));String document=value(uploaded,"documentId");
        assertTrue(service.runNext("initial-worker"));
        var initial=jobs.pages(document,1,1);assertEquals(3,initial.size());
        assertEquals(List.of(2,3),initial.stream().filter(page->"FAILED".equals(page.state())).map(IngestionJobRepository.Page::unitNo).toList());
        var unselectedFailure=initial.get(2);assertEquals("MODEL_HTTP_503",unselectedFailure.errorCode());
        assertEquals("PARTIAL",doc(document).get("processing_state"));
        clearInvocations(ocr);
        doReturn(new OcrResult("Recovered selected source.","Recovered selected source.",null,"ocr-fixture",1,true)).when(ocr).recognize(any());
        var retried=data(service.retry(document,List.of(2),true));assertTrue(service.runNext("retry-worker"));
        verify(ocr,times(1)).recognize(argThat(request->Integer.valueOf(2).equals(request.pageNumber())));
        verify(ocr,never()).recognize(argThat(request->Integer.valueOf(3).equals(request.pageNumber())));
        var pages=jobs.pages(document,1,2);assertEquals(3,pages.size());
        assertEquals("OCR_READY",pages.get(1).state());assertEquals("Recovered selected source.",pages.get(1).chosenText());
        assertEquals("FAILED",pages.get(2).state());assertEquals("MODEL_HTTP_503",pages.get(2).errorCode());
        assertEquals(unselectedFailure.rawText(),pages.get(2).rawText());
        assertEquals(unselectedFailure.ocrRawText(),pages.get(2).ocrRawText());
        var finished=jobs.get(value(retried,"jobId"));assertEquals("COMPLETED",finished.stage());assertEquals(1,finished.failedPages());
        assertEquals("PARTIAL",doc(document).get("processing_state"));Object active=doc(document).get("active_index_version");
        assertNotNull(active);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE index_version=?",Integer.class,active));
        assertEquals(2,db.queryForObject("SELECT COUNT(DISTINCT ordinal_number) FROM native_rag_chunk WHERE index_version=?",Integer.class,active));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE index_version=? AND page_number=3",Integer.class,active));
    }

    @Test void selectedRetryCanReuseCompletePagesBeforeAnEmptyCancelledRevision() throws Exception {
        var pageAnalysis=spy(new PageAnalysisService());
        service=new RagIngestionService(db,auth,files,jobs,new RagPageExtractor(),pageAnalysis,ocr,
                new RagTextChunker(),new RagIndexVersionRepository(db),embeddings,new ObjectMapper());
        var uploaded=data(service.submit(base,pdf(false),"cancelled-revision.pdf"));String document=value(uploaded,"documentId");
        assertTrue(service.runNext("initial-worker"));
        var original=jobs.pages(document,1,1);assertEquals(3,original.size());
        assertEquals(List.of("TEXT_READY","OCR_READY","BLANK"),original.stream().map(IngestionJobRepository.Page::state).toList());
        Object previousActive=doc(document).get("active_index_version");
        var cancelled=data(service.retry(document,List.of(),false));
        assertEquals("200",service.cancel(value(cancelled,"jobId")).getCode());
        assertEquals("CANCELLED",jobs.get(value(cancelled,"jobId")).stage());assertTrue(jobs.pages(document,1,2).isEmpty());
        clearInvocations(ocr,pageAnalysis);
        doReturn(new OcrResult("Selected scan after cancellation.","Selected scan after cancellation.",null,"ocr-fixture",1,true)).when(ocr).recognize(any());
        var retried=data(service.retry(document,List.of(2),true));assertEquals(previousActive,doc(document).get("active_index_version"));
        assertTrue(service.runNext("retry-worker"));verify(ocr,times(1)).recognize(argThat(request->Integer.valueOf(2).equals(request.pageNumber())));
        verify(pageAnalysis,times(1)).analyze(anyString(),anyBoolean(),anyDouble(),anyBoolean());
        var pages=jobs.pages(document,1,3);assertEquals(3,pages.size());
        assertEquals("TEXT_READY",pages.get(0).state());assertEquals(original.get(0).chosenText(),pages.get(0).chosenText());
        assertEquals("OCR_READY",pages.get(1).state());assertEquals("Selected scan after cancellation.",pages.get(1).chosenText());
        assertEquals("BLANK",pages.get(2).state());assertEquals(original.get(2).rawText(),pages.get(2).rawText());
        assertEquals("COMPLETED",jobs.get(value(retried,"jobId")).stage());assertEquals(0,jobs.get(value(retried,"jobId")).failedPages());
        assertEquals("READY",doc(document).get("processing_state"));Object active=doc(document).get("active_index_version");
        assertNotEquals(previousActive,active);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE index_version=?",Integer.class,active));
        assertEquals(2,db.queryForObject("SELECT COUNT(DISTINCT ordinal_number) FROM native_rag_chunk WHERE index_version=?",Integer.class,active));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE index_version=? AND page_number=3",Integer.class,active));
    }

    @Test void selectedRetryWithoutACompletePriorCheckpointRequiresFullReparse() throws Exception {
        var uploaded=data(service.submit(base,pdf(false),"cancelled-before-parsing.pdf"));String document=value(uploaded,"documentId");
        String cancelled=value(uploaded,"jobId");assertEquals("200",service.cancel(cancelled).getCode());
        assertEquals("CANCELLED",jobs.get(cancelled).stage());assertTrue(jobs.pages(document,1,1).isEmpty());
        int before=db.queryForObject("SELECT COUNT(*) FROM native_rag_ingestion_job WHERE document_id=?",Integer.class,document);
        var rejected=service.retry(document,List.of(2),true);
        assertEquals("409",rejected.getCode());assertTrue(rejected.getMsg().contains("重新解析"));assertTrue(rejected.getMsg().contains("全部"));
        assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM native_rag_ingestion_job WHERE document_id=?",Integer.class,document));
        assertTrue(jobs.pages(document,1,2).isEmpty());assertEquals("CANCELLED",doc(document).get("processing_state"));
        assertNull(doc(document).get("active_index_version"));assertFalse(service.runNext("retry-worker"));verifyNoInteractions(ocr);
    }

    @Test void queuedCancellationDoesNotPublishAnIndexAndStudentCannotInspectOrRetry() throws Exception {
        var uploaded=data(service.submit(base,pdf(false),"cancel.pdf"));String document=value(uploaded,"documentId"),job=value(uploaded,"jobId");
        login(2);assertEquals("403",service.status(job).getCode());assertEquals("403",service.retry(document,List.of(),false).getCode());assertEquals("403",service.cancel(job).getCode());
        login(1);assertEquals("200",service.cancel(job).getCode());service.runNext("worker");
        assertEquals("CANCELLED",jobs.get(job).stage());assertNull(doc(document).get("active_index_version"));verifyNoInteractions(ocr);
    }

    @Test void cancelledReplacementPreservesThePublishedDocumentAndItsOriginal() throws Exception {
        var initial=data(service.submit(base,"Published original.".getBytes(StandardCharsets.UTF_8),"first.txt"));
        String document=value(initial,"documentId");service.runNext("worker");String old=doc(document).get("active_index_version").toString();
        var updated=data(service.replace(document,pdf(false),"new.pdf"));
        assertEquals(1,((Number)doc(document).get("document_version")).intValue());assertEquals(old,doc(document).get("active_index_version"));
        service.cancel(value(updated,"jobId"));service.runNext("worker");
        assertEquals(1,((Number)doc(document).get("document_version")).intValue());assertEquals(old,doc(document).get("active_index_version"));
        String key=db.queryForObject("SELECT file_key FROM native_rag_document WHERE id=?",String.class,document);
        assertEquals("Published original.",Files.readString(files.path(key)));
    }

    @Test void missingOcrDoesNotFabricateRecognizedTextOrPublishAnEmptyIndex() throws Exception {
        when(ocr.recognize(any())).thenThrow(new ModelConfigurationException("MODEL_NOT_CONFIGURED"));
        var uploaded=data(service.submit(base,pdf(true),"scan.pdf"));String document=value(uploaded,"documentId");service.runNext("worker");
        assertEquals("FAILED",jobs.get(value(uploaded,"jobId")).stage());assertNull(doc(document).get("active_index_version"));
        assertTrue(jobs.pages(document,1,1).stream().allMatch(page->page.chosenText()==null || page.chosenText().isEmpty()));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,document));
    }

    @Test void incompleteOcrRetainsReviewableRawOutputWithoutCachingOrPublishingIt() throws Exception {
        when(ocr.recognize(any())).thenReturn(new OcrResult("<|ref|>Partial scanned source<|/ref|>",
                "Partial scanned source",null,"ocr-fixture",1,true,"OCR_OUTPUT_TRUNCATED"));
        var uploaded=data(service.submit(base,pdf(true),"incomplete.pdf"));String document=value(uploaded,"documentId");
        service.runNext("worker");
        var page=jobs.pages(document,1,1).getFirst();
        assertEquals("FAILED",page.state());assertEquals("OCR_OUTPUT_TRUNCATED",page.errorCode());
        assertTrue(page.ocrRawText().contains("Partial scanned source"));assertTrue(page.needsReview());
        assertEquals("Partial scanned source",page.ocrText());assertTrue(page.chosenText().isEmpty());
        assertNull(page.ocrFingerprint());assertNull(doc(document).get("active_index_version"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,document));
    }

    @Test void invalidFilesAndLimitsDoNotLeaveQueuedJobsOrOriginalObjects() throws Exception {
        assertEquals("400",service.submit(base,"broken".getBytes(StandardCharsets.UTF_8),"broken.pdf").getCode());
        assertEquals("400",service.submit(base,new byte[20*1024*1024+1],"large.pdf").getCode());
        assertEquals("400",service.submit(base,"old".getBytes(StandardCharsets.UTF_8),"old.doc").getCode());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_document",Integer.class));
        try(var paths=Files.list(temp.resolve("private"))){assertEquals(0,paths.count());}
    }

    @Test void cancellingDuringOcrInterruptsTheCallAndCannotPublishAnIndex() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);
        when(ocr.recognize(any())).thenAnswer(call->{entered.countDown();new java.util.concurrent.CountDownLatch(1).await();return null;});
        var uploaded=data(service.submit(base,pdf(true),"cancel-running.pdf"));
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future=executor.submit(()->service.runNext("cancel-worker"));
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("200",service.cancel(value(uploaded,"jobId")).getCode());
            future.get(2,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("CANCELLED",jobs.get(value(uploaded,"jobId")).stage());
            assertNull(doc(value(uploaded,"documentId")).get("active_index_version"));
        } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
    }

    static byte[] pdf(boolean scanOnly) throws Exception {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            if(!scanOnly){var page=new PDPage();document.addPage(page);try(var text=new PDPageContentStream(document,page)){
                text.beginText();text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),14);text.newLineAtOffset(40,700);
                text.showText("Native course text: gradient descent updates parameters to minimize training loss.");text.endText();}}
            var page=new PDPage();document.addPage(page);var image=new java.awt.image.BufferedImage(1200,500,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics=image.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,1200,500);
            graphics.setColor(java.awt.Color.BLACK);graphics.setFont(new java.awt.Font("SansSerif",0,48));graphics.drawString("SCANNED GRADIENT SOURCE",30,180);graphics.dispose();
            try(var content=new PDPageContentStream(document,page)){content.drawImage(LosslessFactory.createFromImage(document,image),0,200,612,400);}
            if(!scanOnly)document.addPage(new PDPage());document.save(output);return output.toByteArray();
        }
    }
}
