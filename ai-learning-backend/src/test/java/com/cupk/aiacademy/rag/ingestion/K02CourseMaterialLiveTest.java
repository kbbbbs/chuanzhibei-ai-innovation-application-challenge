package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ocr.*;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Explicit paid OCR acceptance on supplied local course PDFs. No source text or pixels are printed. */
@EnabledIfEnvironmentVariable(named="K02_LIVE_OCR_CHECK",matches="true")
class K02CourseMaterialLiveTest {
    private static final Path MATERIALS=Path.of("D:/PPT/人工智能");
    private static final Path PRIVATE_OUTPUT=Path.of("target/k02-live");
    private static final Duration WORKER_TIMEOUT=Duration.ofMinutes(40);
    private static final int DPI=144;
    private static final float JPEG_QUALITY=0.9f;
    @TempDir Path temporary;
    private final ObjectMapper json=new ObjectMapper();
    private final List<Submitted> submitted=new ArrayList<>();
    private JdbcTemplate db;
    private IngestionJobRepository jobs;
    private RagIngestionService service;
    private String base;
    private final AtomicInteger activeWorkers=new AtomicInteger(),maximumWorkers=new AtomicInteger();
    private record Submitted(String id,String jobId,String name,int expectedPages) { }
    private record CopySource(Path path,int pageNo) { }

    @Test void originalCoursePdfsScannedCopiesAndMixedPagesHaveRealPersistedOcrCheckpoints() throws Exception {
        Files.createDirectories(PRIVATE_OUTPUT);
        var report=new LinkedHashMap<String,Object>();
        report.put("status","FAIL");report.put("realOcr",true);report.put("embeddingEnabled",false);
        report.put("storage","isolated H2 and private temporary files");
        report.put("accuracyClaim","No OCR accuracy percentage is inferred from successful calls or character counts.");
        report.put("renderDpi",DPI);report.put("scanJpegQuality",JPEG_QUALITY);
        String phase="CONFIGURATION",failure=null;
        long started=System.nanoTime();
        Map<Path,String> originalHashes=new LinkedHashMap<>();
        try {
            var settings=new RuntimeModelProperties();
            settings.getOcr().setBaseUrl(setting("OCR_BASE_URL"));settings.getOcr().setModel(setting("OCR_MODEL"));
            settings.getOcr().setApiKey(setting("OCR_API_KEY","OCR_APIKEY","SILICONFLOW_API_KEY"));
            settings.getOcr().setTimeoutSeconds(timeout());
            check(!settings.getOcr().getBaseUrl().isBlank() && !settings.getOcr().getModel().isBlank()
                    && !settings.getOcr().getApiKey().isBlank(),"LIVE_OCR_CONFIGURATION_MISSING");
            report.put("ocrModel",settings.getOcr().getModel());
            initialize(new OcrModelAdapter(settings,new OcrModelClient(settings,json),new OcrResultNormalizer(),2,2));
            phase="ORIGINAL_PDF_SUBMISSION";
            List<Path> originals;
            try(var files=Files.list(MATERIALS)){originals=files.filter(path->path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")).sorted().toList();}
            check(originals.size()==8,"EXPECTED_EIGHT_ORIGINAL_PDFS");
            List<Submitted> originalRuns=new ArrayList<>();
            for(Path path:originals) {
                byte[] bytes=Files.readAllBytes(path);originalHashes.put(path,sha(bytes));
                int pages;try(var pdf=Loader.loadPDF(bytes)){pages=pdf.getNumberOfPages();}
                check(pages>=14 && pages<=200,"ORIGINAL_PAGE_COUNT_OUTSIDE_CONTRACT");
                originalRuns.add(submit(bytes,path.getFileName().toString(),pages));
            }
            phase="ORIGINAL_PDF_PROCESSING";drain();
            var originalResults=new ArrayList<Map<String,Object>>();
            for(Submitted run:originalRuns) {
                var result=summary(run);result.put("sourceSha256",originalHashes.get(MATERIALS.resolve(run.name())));
                originalResults.add(result);
            }
            report.put("originals",originalResults);
            for(var result:originalResults)validateReady(result,"ORIGINAL");

            phase="CREATE_SCAN_COPY";
            Path cnn=originals.stream().filter(path->path.getFileName().toString().contains("卷积神经网络")).findFirst().orElseThrow(()->new LiveFailure("CNN_SOURCE_NOT_FOUND"));
            List<CopySource> sources=new ArrayList<>();
            for(Path path:originals){sources.add(new CopySource(path,1));sources.add(new CopySource(path,14));}
            for(int page:List.of(2,3,15,16))sources.add(new CopySource(cnn,page));
            check(sources.size()==20,"SCAN_COPY_PAGE_COUNT_MISMATCH");
            List<Map<String,Object>> manifest=new ArrayList<>();
            byte[] scan;
            int cnn14Unit=0;
            try(var copy=new PDDocument();var output=new ByteArrayOutputStream()) {
                for(int index=0;index<sources.size();index++) {
                    CopySource source=sources.get(index);
                    try(var pdf=Loader.loadPDF(Files.readAllBytes(source.path()))) {
                        BufferedImage image=render(pdf,source.pageNo());
                        try {
                            appendImage(copy,image);
                            if(source.path().equals(cnn) && source.pageNo()==14) {
                                cnn14Unit=index+1;
                                check(ImageIO.write(image,"png",PRIVATE_OUTPUT.resolve("cnn14-source.png").toFile()),"CNN_PREVIEW_ENCODER_UNAVAILABLE");
                            }
                        } finally {image.flush();}
                    }
                    manifest.add(Map.of("copyUnit",index+1,"sourceName",source.path().getFileName().toString(),
                            "sourcePage",source.pageNo(),"sourceSha256",originalHashes.get(source.path())));
                }
                copy.save(output);scan=output.toByteArray();
            }
            check(scan.length<=RagPageExtractor.MAX_BYTES,"SCAN_COPY_EXCEEDS_20_MB");
            Files.write(PRIVATE_OUTPUT.resolve("scan-20-pages.pdf"),scan);
            report.put("scanManifest",manifest);report.put("scanCopySha256",sha(scan));report.put("scanCopyBytes",scan.length);
            report.put("cnn14CopyUnit",cnn14Unit);
            phase="SCAN_COPY_PROCESSING";
            Submitted scanRun=submit(scan,"scan-20-pages.pdf",20);drain();
            var scanResult=summary(scanRun);report.put("scanCopy",scanResult);validateReady(scanResult,"SCAN_COPY");
            List<IngestionJobRepository.Page> scannedPages=jobPages(scanRun);
            // Private review artifact only; complete course text never enters the public test report or stdout.
            Files.writeString(PRIVATE_OUTPUT.resolve("scan-page-results-private.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(scannedPages),StandardCharsets.UTF_8);
            check(scannedPages.stream().anyMatch(page->"OCR_READY".equals(page.state()) && page.chosenText()!=null && !page.chosenText().isBlank()),"NO_REAL_SCAN_OCR_SUCCESS");
            final int cnnUnit=cnn14Unit;
            var recognized=scannedPages.stream().filter(page->page.unitNo()==cnnUnit).findFirst().orElseThrow(()->new LiveFailure("CNN14_CHECKPOINT_MISSING"));
            check("OCR_READY".equals(recognized.state()) && recognized.chosenText()!=null && !recognized.chosenText().isBlank(),"CNN14_OCR_NOT_READY");
            Files.writeString(PRIVATE_OUTPUT.resolve("cnn14-recognized.txt"),recognized.chosenText(),StandardCharsets.UTF_8);
            report.put("cnn14RecognizedCharacters",recognized.chosenText().length());
            report.put("cnn14NeedsReview",recognized.needsReview());

            phase="MIXED_COPY_PROCESSING";
            byte[] mixed=mixed(cnn);Files.write(PRIVATE_OUTPUT.resolve("mixed-3-pages.pdf"),mixed);
            report.put("mixedCopySha256",sha(mixed));
            report.put("mixedManifest",List.of(Map.of("copyUnit",1,"type","synthetic native text"),
                    Map.of("copyUnit",2,"sourceName",cnn.getFileName().toString(),"sourcePage",14,"sourceSha256",originalHashes.get(cnn)),
                    Map.of("copyUnit",3,"type","synthetic blank")));
            Submitted mixedRun=submit(mixed,"mixed-3-pages.pdf",3);drain();
            var mixedResult=summary(mixedRun);report.put("mixedCopy",mixedResult);validateReady(mixedResult,"MIXED_COPY");
            var mixedPages=jobPages(mixedRun);
            check("TEXT_READY".equals(mixedPages.getFirst().state()),"MIXED_NATIVE_PAGE_NOT_PRESERVED");
            check("BLANK".equals(mixedPages.getLast().state()) && (mixedPages.getLast().chosenText()==null || mixedPages.getLast().chosenText().isEmpty()),"MIXED_BLANK_PAGE_BECAME_CONTENT");

            phase="INVALID_AND_LIMITS";
            int before=db.queryForObject("SELECT COUNT(*) FROM native_rag_ingestion_job",Integer.class);
            var limits=new LinkedHashMap<String,String>();
            limits.put("brokenPdf",service.submit(base,"not a pdf".getBytes(StandardCharsets.UTF_8),"broken.pdf").getCode());
            limits.put("over20Mb",service.submit(base,new byte[RagPageExtractor.MAX_BYTES+1],"too-large.pdf").getCode());
            byte[] tooMany;
            try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
                for(int i=0;i<201;i++)document.addPage(new PDPage());document.save(output);tooMany=output.toByteArray();
            }
            limits.put("over200Pages",service.submit(base,tooMany,"201-pages.pdf").getCode());report.put("limitChecks",limits);
            check(limits.values().stream().allMatch("400"::equals),"INVALID_INPUT_NOT_REJECTED");
            check(before==db.queryForObject("SELECT COUNT(*) FROM native_rag_ingestion_job",Integer.class),"INVALID_INPUT_LEFT_JOB");
            for(var original:originalHashes.entrySet())check(original.getValue().equals(sha(Files.readAllBytes(original.getKey()))),"ORIGINAL_SOURCE_CHANGED");
            report.put("originalFilesUnchanged",true);report.put("maximumWorkers",maximumWorkers.get());
            check(maximumWorkers.get()<=2,"WORKER_CONCURRENCY_EXCEEDED");
            phase="COMPLETE";report.put("status","PASS");
        } catch(Throwable error) {
            failure=error instanceof LiveFailure?error.getMessage():"K02_LIVE_"+phase+"_"+error.getClass().getSimpleName();
            report.put("failureCode",failure);
        } finally {
            report.put("phase",phase);report.put("elapsedMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            report.put("maximumWorkers",maximumWorkers.get());
            if(jobs!=null)for(Submitted run:submitted)try{jobs.requestCancel(run.jobId());}catch(RuntimeException ignored){}
            UserContext.clear();
            Files.writeString(PRIVATE_OUTPUT.resolve("course-materials.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report),StandardCharsets.UTF_8);
            // This line deliberately excludes paths, raw OCR, provider bodies, credentials and exception causes.
            System.out.println("K02_COURSE_MATERIAL_LIVE_RESULT "+json.writeValueAsString(Map.of("status",report.get("status"),"phase",phase,"elapsedMs",report.get("elapsedMs"))));
            if(db!=null)db.execute("DROP ALL OBJECTS");
        }
        if(failure!=null)fail(failure+"; see private target/k02-live/course-materials.json");
    }

    private void initialize(OcrProvider ocr) {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa",""));
        new RagSchemaMigrator(db).migrate();
        var permissions=mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(invocation->{
            int id=invocation.getArgument(0);if(id!=1)throw new ForbiddenException("Unexpected live-test identity");
            var actor=new AuthUtils.LoginUser();actor.setUserId(1);actor.setRoleCode("teacher");return actor;
        });
        var auth=new RagAuthorizationService(permissions,db);
        var files=new RagFileStore(temporary.resolve("private-originals").toString());files.initialize();
        jobs=new IngestionJobRepository(db);
        service=new RagIngestionService(db,auth,files,jobs,new RagPageExtractor(),new PageAnalysisService(),ocr,
                new RagTextChunker(),new RagIndexVersionRepository(db),new RagEmbeddingClient(false,"","","",json),json);
        base=UUID.randomUUID().toString();db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,1,'K02 course material live','private local verification')",base);
        var teacher=new AuthUtils.LoginUser();teacher.setUserId(1);teacher.setRoleCode("teacher");UserContext.set(teacher);
    }
    private Submitted submit(byte[] bytes,String name,int pages) {
        Result response=service.submit(base,bytes,name);check("200".equals(response.getCode()),"COURSE_MATERIAL_SUBMISSION_REJECTED");
        check(response.getData() instanceof Map<?,?>,"UPLOAD_RESPONSE_SHAPE_INVALID");
        var data=(Map<?,?>)response.getData();
        Submitted run=new Submitted(data.get("documentId").toString(),data.get("jobId").toString(),name,pages);submitted.add(run);return run;
    }
    private void drain() throws Exception {
        var executor=Executors.newFixedThreadPool(2);List<Future<?>> pending=new ArrayList<>();
        long deadline=System.nanoTime()+WORKER_TIMEOUT.toNanos();
        try {
            for(int index=0;index<2;index++) {
                final String worker="course-live-"+index;
                pending.add(executor.submit(()->{
                    int current=activeWorkers.incrementAndGet();maximumWorkers.accumulateAndGet(current,Math::max);
                    try{while(service.runNext(worker)){if(Thread.currentThread().isInterrupted())return;}}
                    finally{activeWorkers.decrementAndGet();}
                }));
            }
            for(var task:pending)task.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
        } finally {
            if(pending.stream().anyMatch(task->!task.isDone()))for(Submitted run:submitted)try{jobs.requestCancel(run.jobId());}catch(RuntimeException ignored){}
            pending.forEach(task->task.cancel(true));executor.shutdownNow();
            check(executor.awaitTermination(30,TimeUnit.SECONDS),"LIVE_WORKERS_DID_NOT_STOP");
        }
    }
    private Map<String,Object> summary(Submitted run) {
        var job=jobs.get(run.jobId());var pages=jobs.pages(run.id(),job.documentVersion(),job.parseRevision());
        var document=db.queryForMap("SELECT processing_state,active_index_version,chunk_count FROM native_rag_document WHERE id=?",run.id());
        var result=new LinkedHashMap<String,Object>();
        result.put("sourceName",run.name());result.put("jobStage",job.stage());result.put("documentState",document.get("processing_state"));
        result.put("expectedPages",run.expectedPages());result.put("checkpointCount",pages.size());
        result.put("activeIndexAvailable",document.get("active_index_version")!=null);result.put("chunkCount",document.get("chunk_count"));
        result.put("textPages",pages.stream().filter(page->"TEXT_READY".equals(page.state())).count());
        result.put("ocrPages",pages.stream().filter(page->"OCR_READY".equals(page.state())).count());
        result.put("blankPages",pages.stream().filter(page->"BLANK".equals(page.state())).count());
        result.put("failedPages",pages.stream().filter(page->"FAILED".equals(page.state())).map(page->Map.of("page",page.unitNo(),"errorCode",page.errorCode()==null?"UNKNOWN":page.errorCode())).toList());
        result.put("chosenCharacters",pages.stream().mapToLong(page->page.chosenText()==null?0:page.chosenText().length()).sum());
        result.put("needsReviewPages",pages.stream().filter(IngestionJobRepository.Page::needsReview).map(IngestionJobRepository.Page::unitNo).toList());
        var times=db.queryForMap("SELECT created_at,updated_at FROM native_rag_ingestion_job WHERE id=?",run.jobId());
        if(times.get("created_at") instanceof Timestamp from && times.get("updated_at") instanceof Timestamp to)result.put("jobWallMs",Math.max(0,to.getTime()-from.getTime()));
        result.put("jobErrorCode",job.errorCode());return result;
    }
    private List<IngestionJobRepository.Page> jobPages(Submitted run){var job=jobs.get(run.jobId());return jobs.pages(run.id(),job.documentVersion(),job.parseRevision());}
    private void validateReady(Map<String,Object> result,String label) {
        check("COMPLETED".equals(result.get("jobStage")),label+"_JOB_NOT_COMPLETED");
        check(Set.of("READY","PARTIAL").contains(result.get("documentState")),label+"_STATE_NOT_READY_OR_PARTIAL");
        check(result.get("expectedPages").equals(result.get("checkpointCount")),label+"_CHECKPOINT_COVERAGE_MISMATCH");
        check(Boolean.TRUE.equals(result.get("activeIndexAvailable")) && ((Number)result.get("chunkCount")).intValue()>0,label+"_NO_USABLE_ACTIVE_INDEX");
        boolean hasFailure=!((List<?>)result.get("failedPages")).isEmpty();
        check(hasFailure=="PARTIAL".equals(result.get("documentState")),label+"_FAILED_PAGE_STATE_MISMATCH");
    }
    private BufferedImage render(PDDocument document,int pageNo) throws Exception {
        var renderer=new PDFRenderer(document);renderer.setSubsamplingAllowed(true);
        var image=renderer.renderImageWithDPI(pageNo-1,DPI,ImageType.RGB);
        check((long)image.getWidth()*image.getHeight()<=8_000_000L,"SCAN_RENDER_PIXEL_LIMIT");return image;
    }
    private void appendImage(PDDocument document,BufferedImage image) throws Exception {
        float width=image.getWidth()*72f/DPI,height=image.getHeight()*72f/DPI;
        var page=new PDPage(new PDRectangle(width,height));document.addPage(page);
        var jpeg=JPEGFactory.createFromImage(document,image,JPEG_QUALITY,DPI);
        try(var content=new PDPageContentStream(document,page)){content.drawImage(jpeg,0,0,width,height);}
    }
    private byte[] mixed(Path cnn) throws Exception {
        try(var document=new PDDocument();var source=Loader.loadPDF(Files.readAllBytes(cnn));var output=new ByteArrayOutputStream()) {
            var nativePage=new PDPage();document.addPage(nativePage);
            try(var content=new PDPageContentStream(document,nativePage)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),14);content.newLineAtOffset(40,700);
                content.showText("Synthetic native text: gradient descent updates parameters to minimize loss.");content.endText();
            }
            var image=render(source,14);try{appendImage(document,image);}finally{image.flush();}
            document.addPage(new PDPage());document.save(output);return output.toByteArray();
        }
    }
    private static String setting(String...names){for(String name:names){String value=System.getenv(name);if(value!=null && !value.isBlank())return value.strip();}return "";}
    private static int timeout(){try{return Math.max(1,Math.min(180,Integer.parseInt(setting("OCR_TIMEOUT_SECONDS"))));}catch(NumberFormatException ignored){return 60;}}
    private static String sha(byte[] value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static void check(boolean condition,String code){if(!condition)throw new LiveFailure(code);}
    private static class LiveFailure extends RuntimeException{LiveFailure(String code){super(code);}}
}
