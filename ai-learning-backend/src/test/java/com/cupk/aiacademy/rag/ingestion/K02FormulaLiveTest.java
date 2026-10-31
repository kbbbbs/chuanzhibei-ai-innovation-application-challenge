package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.cupk.aiacademy.rag.ocr.*;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Paid, opt-in production ingestion test. Original pixels/text remain under ignored target/. */
@EnabledIfEnvironmentVariable(named="K02_FORMULA_LIVE_CHECK",matches="true")
class K02FormulaLiveTest {
    @TempDir Path temp;
    @Test void realFormulaPagesAndTheirScannedCopiesKeepStructuredEvidence() throws Exception {
        var json=new ObjectMapper();Path output=Path.of("target/k02-formulas");Files.createDirectories(output);
        var report=new LinkedHashMap<String,Object>();var summaries=new ArrayList<Map<String,Object>>();
        var reviews=new ArrayList<Map<String,Object>>();report.put("samples",summaries);report.put("status","FAIL");
        report.put("embeddingEnabled",false);report.put("accuracyClaim","Structural checks do not establish mathematical accuracy.");
        long started=System.nanoTime();var fixture=new K02IngestionServiceTest();fixture.temp=temp;fixture.setup();
        try {
            var config=new RuntimeModelProperties();config.getOcr().setBaseUrl(setting("OCR_BASE_URL"));
            config.getOcr().setModel(setting("OCR_MODEL"));config.getOcr().setApiKey(setting("OCR_API_KEY","OCR_APIKEY","SILICONFLOW_API_KEY"));
            config.getOcr().setTimeoutSeconds(120);report.put("ocrModel",config.getOcr().getModel());
            var provider=new OcrModelAdapter(config,new OcrModelClient(config,json),new OcrResultNormalizer());
            fixture.service=new RagIngestionService(fixture.db,fixture.auth,fixture.files,fixture.jobs,new RagPageExtractor(),new PageAnalysisService(),provider,
                    new RagTextChunker(),new RagIndexVersionRepository(fixture.db),fixture.embeddings,json);
            int[][] selection={{3,14},{3,23},{4,10},{4,18},{4,23},{5,14}};
            List<Path> originals;try(var list=Files.list(Path.of("D:/PPT/人工智能"))){originals=list.filter(p->p.toString().endsWith(".pdf")).toList();}
            for(var sample:selection) {
                Path original=originals.stream().filter(p->p.getFileName().toString().contains("第"+sample[0]+"章")).findFirst().orElseThrow();
                byte[] bytes=Files.readAllBytes(original);String originalHash=sha(bytes),tag="ch"+sample[0]+"-p"+sample[1];
                byte[] pixels;try(var source=new RagPageExtractor().open(bytes,original.getFileName().toString())){pixels=source.renderPng(sample[1]);}
                Files.write(output.resolve(tag+"-production.png"),pixels);
                for(boolean scanned:new boolean[]{false,true}) {
                    byte[] input;
                    try(var source=Loader.loadPDF(bytes);var copy=new PDDocument();var buffer=new ByteArrayOutputStream()) {
                        if(!scanned)copy.importPage(source.getPage(sample[1]-1));
                        else {
                            var page=new PDPage(source.getPage(sample[1]-1).getCropBox());copy.addPage(page);
                            try(var content=new PDPageContentStream(copy,page)) {
                                content.drawImage(LosslessFactory.createFromImage(copy,ImageIO.read(new ByteArrayInputStream(pixels))),
                                        page.getCropBox().getLowerLeftX(),page.getCropBox().getLowerLeftY(),page.getCropBox().getWidth(),page.getCropBox().getHeight());
                            }
                        }
                        copy.save(buffer);input=buffer.toByteArray();
                    }
                    String name=tag+(scanned?"-scan":"-original")+".pdf";
                    Files.write(output.resolve(name),input);
                    var upload=fixture.data(fixture.service.submit(fixture.base,input,name));String id=fixture.value(upload,"documentId");
                    assertTrue(fixture.service.runNext("formula-live"));var page=fixture.jobs.pages(id,1,1).getFirst();
                    var metadata=json.readTree(page.formulaJson());var stat=new LinkedHashMap<String,Object>();
                    stat.put("tag",tag);stat.put("sourceFile",original.getFileName().toString());stat.put("sourceSha256",originalHash);
                    stat.put("sourcePage",sample[1]);stat.put("copyPage",1);stat.put("scanned",scanned);stat.put("inputSha256",sha(input));
                    stat.put("pageState",page.state());stat.put("errorCode",page.errorCode());stat.put("needsReview",page.needsReview());
                    stat.put("formulaCount",metadata.path("blocks").size());stat.put("structuralIssues",metadata.path("issues"));
                    var location=json.readTree(page.locationJson());var publicLocation=new LinkedHashMap<String,Object>();
                    for(String field:List.of("ocrMode","latencyMs","initialOcrMode","initialOcrLatencyMs","resolutionRetryScale","resolutionRetryReason"))
                        if(location.has(field))publicLocation.put(field,location.get(field));
                    stat.put("location",publicLocation);
                    stat.put("chunks",fixture.db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,id));
                    summaries.add(stat);
                    var review=new LinkedHashMap<String,Object>();review.put("tag",tag);review.put("scanned",scanned);
                    review.put("rawOcr",page.ocrRawText());review.put("chosenText",page.chosenText());review.put("formulaMetadata",metadata);reviews.add(review);
                }
                assertEquals(originalHash,sha(Files.readAllBytes(original)),"Original source was modified");
            }
            long ready=summaries.stream().filter(s->"OCR_READY".equals(s.get("pageState")) || "TEXT_READY".equals(s.get("pageState"))).count();
            report.put("ready",ready);report.put("total",summaries.size());
            report.put("formulaPages",summaries.stream().filter(s->((Number)s.get("formulaCount")).intValue()>0).count());
            assertEquals(12,ready,"See private per-page report for actual error codes");
            assertTrue(summaries.stream().filter(s->!s.get("tag").equals("ch5-p14")).allMatch(s->((Number)s.get("formulaCount")).intValue()>0),"A formula source has no structured formula");
            report.put("status","PASS");
        } finally {
            report.put("elapsedMs",(System.nanoTime()-started)/1_000_000);
            Files.writeString(output.resolve("formula-live-report.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            Files.writeString(output.resolve("formula-live-private.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(reviews));
            fixture.cleanup();
        }
    }
    private static String setting(String... names){for(var name:names){String value=System.getenv(name);if(value!=null&&!value.isBlank())return value;}return "";}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
