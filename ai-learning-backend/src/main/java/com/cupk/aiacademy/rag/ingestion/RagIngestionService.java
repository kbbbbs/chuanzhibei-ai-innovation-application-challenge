package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.model.ModelConfigurationException;
import com.cupk.aiacademy.model.ModelInvocationException;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ocr.*;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import com.cupk.aiacademy.rag.formula.FormulaText;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Application-owned asynchronous ingestion. Originals, page checkpoints and ready indexes have separate lifecycles. */
@Service
public class RagIngestionService {
    private static final Duration LEASE=Duration.ofSeconds(180);
    private static final Set<String> TERMINAL=Set.of("COMPLETED","FAILED","CANCELLED");
    private final JdbcTemplate db;
    private final RagAuthorizationService auth;
    private final RagFileStore files;
    private final IngestionJobRepository jobs;
    private final RagPageExtractor extractor;
    private final PageAnalysisService analysis;
    private final OcrProvider ocr;
    private final RagTextChunker chunker;
    private final RagIndexVersionRepository indexes;
    private final RagEmbeddingClient embeddings;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final OcrResultNormalizer normalizer=new OcrResultNormalizer();
    private final PdfPageRenderer rasterChecks=new PdfPageRenderer();
    private final com.cupk.aiacademy.rag.visual.RagVisualService visuals;
    private final ConcurrentMap<String,Thread> running=new ConcurrentHashMap<>();

    public RagIngestionService(JdbcTemplate db,RagAuthorizationService auth,RagFileStore files,
            IngestionJobRepository jobs,RagPageExtractor extractor,PageAnalysisService analysis,OcrProvider ocr,
            RagTextChunker chunker,RagIndexVersionRepository indexes,RagEmbeddingClient embeddings,ObjectMapper json) {
        this.db=db;this.auth=auth;this.files=files;this.jobs=jobs;this.extractor=extractor;this.analysis=analysis;
        this.ocr=ocr;this.chunker=chunker;this.indexes=indexes;this.embeddings=embeddings;this.json=json;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()));
        this.visuals=new com.cupk.aiacademy.rag.visual.RagVisualService(db,auth,json);
    }

    public Result submit(String baseId,byte[] bytes,String filename){return result(()->submitOriginal(baseId,null,bytes,filename));}
    public Result replace(String id,byte[] bytes,String filename){return result(()->{
        var document=ownedDocument(id);return submitOriginal(text(document,"kb_id"),document,bytes,filename);
    });}

    private Map<String,Object> submitOriginal(String baseId,Map<String,Object> previous,byte[] bytes,String filename) {
        RagActor actor=auth.currentActor();ownedBase(baseId,actor);
        String name=safeFilename(filename),mime;int pageCount;
        try(var source=extractor.open(bytes,name)){mime=source.mimeType();pageCount=source.pageCount();}
        catch(java.io.IOException error){throw failure("400","文件结构读取失败");}
        String sha=hash(bytes),key=UUID.randomUUID().toString();
        db.update("INSERT INTO native_rag_file_cleanup(file_key,state) VALUES(?,'STAGING')",key);
        try {
            files.store(key,bytes);
            Map<String,Object> uploaded=transaction.execute(status->{
                ownedBase(baseId,actor);
                db.queryForList("SELECT id FROM native_rag_base WHERE id=? FOR UPDATE",baseId);
                var duplicate=db.queryForList("SELECT DISTINCT d.* FROM native_rag_document d JOIN native_rag_document_version v ON d.id=v.document_id WHERE d.kb_id=? AND v.sha256=? AND (v.document_version=d.document_version OR v.document_version=d.processing_version)",baseId,sha);
                if(!duplicate.isEmpty()) {
                    if(previous!=null && !text(previous,"id").equals(text(duplicate.getFirst(),"id")))throw failure("409","同一资料已经属于本库其他文档");
                    return uploadView(duplicate.getFirst(),true);
                }
                String id=previous==null?UUID.randomUUID().toString():text(previous,"id");
                Map<String,Object> current=previous==null?null:lockedDocument(id);
                if(current!=null)ensureIdle(current);
                int version=current==null?1:db.queryForObject("SELECT COALESCE(MAX(document_version),0)+1 FROM native_rag_document_version WHERE document_id=?",Integer.class,id);
                String active=current==null?null:nullable(current,"active_index_version");
                int expected=current==null?1:number(current,"document_version");
                if(current==null) {
                    if(db.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE kb_id=?",Integer.class,baseId)>=200)throw failure("400","单库最多200份资料");
                    db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status,document_version,parse_revision,processing_state,processing_version) VALUES(?,?,?,?,?,?,?,?,?,0,'pending',1,1,'QUEUED',1)",
                            id,baseId,name,name,key,sha,mime,bytes.length,pageCount);
                }
                db.update("INSERT INTO native_rag_document_version(document_id,document_version,file_key,sha256,file_name,mime_type,file_size,actor_id) VALUES(?,?,?,?,?,?,?,?)",id,version,key,sha,name,mime,bytes.length,actor.userId());
                String job=jobs.enqueue(actor.userId(),id,version,1,active,expected,List.of(),List.of());
                db.update("UPDATE native_rag_document SET processing_state='QUEUED',processing_job_id=?,processing_version=? WHERE id=?",job,version,id);
                db.update("DELETE FROM native_rag_file_cleanup WHERE file_key=?",key);
                return uploadView(document(id),false);
            });
            if(Boolean.TRUE.equals(uploaded.get("duplicate")))discard(key);
            return uploaded;
        } catch(RuntimeException error){discard(key);throw error;}
    }

    public Result retry(String id,List<Integer> pages,boolean forceOcr){return result(()->{
        RagActor actor=auth.currentActor();var document=ownedDocument(id);
        return transaction.execute(status->{
            var current=lockedDocument(id);ownedBase(text(current,"kb_id"),actor);ensureIdle(current);
            int version=current.get("processing_version") instanceof Number n?n.intValue():number(current,"document_version");
            var source=version(id,version);int units;
            try(var opened=extractor.open(originalBytes(source),text(source,"file_name"))){units=opened.unitCount();}
            catch(java.io.IOException error){throw failure("400","文件结构读取失败");}
            List<Integer> selected=checkedPages(pages,units);
            if(!selected.isEmpty() && db.queryForObject("SELECT COUNT(DISTINCT page_no) FROM native_rag_page WHERE document_id=? AND document_version=? AND page_no BETWEEN 1 AND ?",
                    Integer.class,id,version,units)<units)
                throw failure("409","页面记录尚不完整，请选择重新解析全部");
            List<Integer> force=forceOcr?(selected.isEmpty()?java.util.stream.IntStream.rangeClosed(1,units).boxed().toList():selected):List.of();
            int previous=db.queryForObject("SELECT COALESCE(MAX(parse_revision),0) FROM native_rag_ingestion_job WHERE document_id=? AND document_version=?",Integer.class,id,version);
            int revision=Math.max(previous,version==number(current,"document_version")?number(current,"parse_revision"):0)+1;
            String job=jobs.enqueue(actor.userId(),id,version,revision,nullable(current,"active_index_version"),number(current,"document_version"),force,selected);
            db.update("UPDATE native_rag_document SET processing_job_id=?,processing_version=?,processing_state='QUEUED' WHERE id=?",job,version,id);
            return uploadView(document(id),false);
        });
    });}

    public record PageEdit(Integer pageNo,String text) { }

    /** Saving never starts processing, so several pages can be edited while an earlier update is still running. */
    public Result savePageEdits(String id,Integer targetVersion,List<PageEdit> edits){return result(()->{
        RagActor actor=auth.currentActor();var document=ownedDocument(id);int version=number(document,"document_version");
        if(targetVersion!=null && targetVersion!=version)throw failure("409","资料已更换版本，请刷新后再编辑");
        if(edits==null || edits.isEmpty() || edits.size()>200)throw failure("400","请提交1到200页修改");
        int units=db.queryForObject("SELECT COUNT(DISTINCT page_no) FROM native_rag_page WHERE document_id=? AND document_version=? AND parse_revision=?",
                Integer.class,id,version,number(document,"parse_revision"));
        if(units==0)throw failure("409","资料尚未处理完成，暂不能编辑");
        var latest=new LinkedHashMap<Integer,String>();
        for(var edit:edits) {
            if(edit==null || edit.pageNo()==null || edit.pageNo()<1 || edit.pageNo()>units)throw failure("400","页码不在资料范围内");
            String text=edit.text()==null?"":edit.text().strip();
            if(text.isEmpty())throw failure("400","页面文字不能为空");
            if(text.length()>100_000)throw failure("400","单页文字不能超过10万字");
            latest.put(edit.pageNo(),text);
        }
        latest.forEach((page,text)->db.update("INSERT INTO native_rag_page_edit(document_id,document_version,page_no,edited_text,actor_id) VALUES(?,?,?,?,?)",
                id,version,page,text,actor.userId()));
        return editViews(document(id));
    });}
    public Result pageEdits(String id,Integer targetVersion){return result(()->{
        var document=ownedDocument(id);
        if(targetVersion!=null && targetVersion!=number(document,"document_version"))return List.of();
        return editViews(document);
    });}
    /** One selected-page revision carries every saved change; unchanged pages are copied, not recognized again. */
    public Result applyPageEdits(String id){return result(()->{
        RagActor actor=auth.currentActor();ownedDocument(id);
        return transaction.execute(status->{
            var current=lockedDocument(id);ownedBase(text(current,"kb_id"),actor);ensureIdle(current);
            int version=number(current,"document_version");var published=publishedTexts(current);
            var pending=new ArrayList<Integer>();var rows=new ArrayList<Object>();
            for(var edit:latestEdits(id,version))if(!Objects.equals(edit.get("edited_text"),published.get(number(edit,"page_no")))) {
                pending.add(number(edit,"page_no"));rows.add(edit.get("id"));
            }
            if(pending.isEmpty())throw failure("409","没有需要更新的修改");
            int previous=db.queryForObject("SELECT COALESCE(MAX(parse_revision),0) FROM native_rag_ingestion_job WHERE document_id=? AND document_version=?",Integer.class,id,version);
            int revision=Math.max(previous,number(current,"parse_revision"))+1;
            String job=jobs.enqueue(actor.userId(),id,version,revision,nullable(current,"active_index_version"),version,List.of(),pending);
            for(Object row:rows)db.update("UPDATE native_rag_page_edit SET applied_revision=? WHERE id=?",revision,row);
            db.update("UPDATE native_rag_document SET processing_job_id=?,processing_version=?,processing_state='QUEUED' WHERE id=?",job,version,id);
            return uploadView(document(id),false);
        });
    });}
    private List<Map<String,Object>> latestEdits(String id,int version) {
        var latest=new LinkedHashMap<Integer,Map<String,Object>>();
        for(var row:db.queryForList("SELECT * FROM native_rag_page_edit WHERE document_id=? AND document_version=? ORDER BY page_no,id DESC",id,version))
            latest.putIfAbsent(number(row,"page_no"),row);
        return List.copyOf(latest.values());
    }
    private Map<Integer,String> publishedTexts(Map<String,Object> document) {
        var texts=new HashMap<Integer,String>();
        for(var page:jobs.pages(text(document,"id"),number(document,"document_version"),number(document,"parse_revision")))
            texts.put(page.unitNo(),page.chosenText());
        return texts;
    }
    /** Every saved row, newest first per page; only the newest row of a page carries a live state. */
    private List<Map<String,Object>> editViews(Map<String,Object> document) {
        String id=text(document,"id");int version=number(document,"document_version");var published=publishedTexts(document);
        Integer updating=null;String job=nullable(document,"processing_job_id");
        if(job!=null){var running=jobs.get(job);if(!TERMINAL.contains(running.stage()))updating=running.parseRevision();}
        var views=new ArrayList<Map<String,Object>>();Integer page=null;
        for(var row:db.queryForList("SELECT * FROM native_rag_page_edit WHERE document_id=? AND document_version=? ORDER BY page_no,id DESC",id,version)) {
            boolean latest=!Objects.equals(page,number(row,"page_no"));page=number(row,"page_no");
            Integer applied=row.get("applied_revision") instanceof Number n?n.intValue():null;
            String state=!latest?"SUPERSEDED":Objects.equals(row.get("edited_text"),published.get(page))?"ACTIVE"
                    :updating!=null && updating.equals(applied)?"UPDATING":"SAVED";
            var view=new LinkedHashMap<String,Object>();
            view.put("id",row.get("id"));view.put("pageNo",page);view.put("text",row.get("edited_text"));view.put("state",state);
            view.put("latest",latest);view.put("savedAt",row.get("created_at"));view.put("actorId",row.get("actor_id"));
            views.add(view);
        }
        return views;
    }

    public Result status(String jobId){return result(()->{
        var job=jobs.get(checkedId(jobId));ownedDocument(job.documentId());return jobView(job);
    });}
    public Result cancel(String jobId){return result(()->{
        var job=jobs.get(checkedId(jobId));ownedDocument(job.documentId());
        if(!jobs.requestCancel(jobId))throw failure("409","任务已结束，不能取消");
        Thread current=running.get(jobId);if(current!=null)current.interrupt();
        return jobView(jobs.get(jobId));
    });}

    public Result pages(String id,Integer targetVersion){return result(()->{
        var document=ownedDocument(id);int version=targetVersion==null?processingVersion(document):targetVersion;
        version(id,version);int revision=latestRevision(id,version);
        var values=jobs.pages(id,version,revision).stream().map(page->pageView(page,id,version,revision)).toList();
        ownedDocument(id);return values;
    });}
    public byte[] preview(String id,int unit,Integer targetVersion) {
        var document=ownedDocument(id);int version=targetVersion==null?processingVersion(document):targetVersion;
        var source=version(id,version);byte[] image;
        try(var opened=extractor.open(originalBytes(source),text(source,"file_name"))){image=opened.renderPng(unit);}
        catch(java.io.IOException error){throw failure("500","原页预览失败");}
        ownedDocument(id);return image;
    }

    public byte[] coursePreview(Integer courseId, String id, int unit, int targetVersion) {
        RagActor actor = auth.currentActor();
        auth.requireCourseDocumentRead(actor, courseId, id, targetVersion, false);
        var source = version(id, targetVersion);
        byte[] image;
        try (var opened = extractor.open(originalBytes(source), text(source, "file_name"))) {
            image = opened.renderPng(unit);
        } catch (java.io.IOException error) {
            throw failure("500", "原页预览失败");
        }
        auth.requireCourseDocumentRead(actor, courseId, id, targetVersion, false);
        return image;
    }

    /** Used by the bounded background worker and explicit tests. No HTTP endpoint executes this method. */
    public boolean runNext(String workerId) {
        var claimed=jobs.claimNext(workerId,LEASE);if(claimed.isEmpty())return false;
        process(claimed.get());return true;
    }
    private void process(IngestionJobRepository.Job job) {
        running.put(job.id(),Thread.currentThread());
        var heartbeats=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"rag-job-heartbeat");thread.setDaemon(true);return thread;});
        heartbeats.scheduleAtFixedRate(()->{try{jobs.heartbeat(job,LEASE);}catch(RuntimeException ignored){}},15,15,TimeUnit.SECONDS);
        try {
            authorizeJob(job);var original=version(job.documentId(),job.documentVersion());
            try(var source=extractor.open(originalBytes(original),text(original,"file_name"))) {
                int total=source.unitCount(),failed=0,processed=0;long characters=0;
                advance(job,"PARSING",1,total,0,0);
                Map<Integer,IngestionJobRepository.Page> previous=Map.of();
                if(!job.selectedPages().isEmpty() && job.parseRevision()>1) {
                    previous=historicalPages(job);
                    if(previous.size()!=total)throw failure("409","历史页面记录不完整，请重新解析全部");
                    for(var page:previous.values())if(!job.selectedPages().contains(page.unitNo()) && !jobs.savePage(job,page))
                        throw new Stopped();
                }
                var resumed=new HashMap<Integer,IngestionJobRepository.Page>();
                for(var page:jobs.pages(job.documentId(),job.documentVersion(),job.parseRevision()))
                    if(!"FAILED".equals(page.state()) || !job.selectedPages().isEmpty() && !job.selectedPages().contains(page.unitNo()))
                        resumed.put(page.unitNo(),page);
                for(int unit=1;unit<=total;unit++) {
                    authorizeJob(job);
                    IngestionJobRepository.Page page=resumed.get(unit);
                    if(page==null)page=manualPage(job,previous.get(unit),unit);
                    if(page==null)page=processPage(job,source,original,unit,total,processed,failed);
                    characters+=page.chosenText()==null?0:page.chosenText().length();
                    if(characters>RagPageExtractor.MAX_TEXT)throw failure("400","提取内容超过100万字上限");
                    var checkpoint=page;int currentUnit=unit;
                    // Pixels and metadata become one fenced checkpoint; a stale worker cannot publish images.
                    page=transaction.execute(status->{
                        if(!jobs.savePage(job,checkpoint))throw new Stopped();
                        byte[] preview=null;boolean previewFailed=false;
                        if(source.pageCount()>0 && !"BLANK".equals(checkpoint.state()))
                            try {preview=source.renderPng(currentUnit);}catch(RagAccessException unavailable){previewFailed=true;}
                        visuals.capture(job.documentId(),job.documentVersion(),job.parseRevision(),currentUnit,preview,
                                checkpoint.ocrRawText(),checkpoint.chosenText());
                        if(previewFailed)visuals.issue(job.documentId(),job.documentVersion(),job.parseRevision(),currentUnit,"VISUAL_RENDER_FAILED");
                        if("VISUAL_REFERENCE".equals(checkpoint.method()))visuals.issue(job.documentId(),job.documentVersion(),job.parseRevision(),currentUnit,"VISUAL_ONLY_PAGE");
                        authorizeJob(job);
                        if("VISUAL_REFERENCE".equals(checkpoint.method()) && db.queryForObject(
                                "SELECT COUNT(*) FROM native_rag_visual WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",Integer.class,
                                job.documentId(),job.documentVersion(),job.parseRevision(),currentUnit)==0) {
                            var unavailable=new IngestionJobRepository.Page(checkpoint.unitNo(),checkpoint.rawText(),checkpoint.ocrRawText(),checkpoint.ocrText(),"",
                                    checkpoint.method(),"FAILED",checkpoint.confidence(),"VISUAL_IMAGE_UNAVAILABLE",true,checkpoint.locationJson(),null,checkpoint.formulaJson());
                            if(!jobs.savePage(job,unavailable))throw new Stopped();return unavailable;
                        }
                        return checkpoint;
                    });
                    if("FAILED".equals(page.state()))failed++;processed++;
                    advance(job,"PARSING",Math.min(80,5+processed*75/total),total,processed,failed);
                }
                publish(job,source,original,jobs.pages(job.documentId(),job.documentVersion(),job.parseRevision()),failed);
            }
        } catch(Stopped stopped) {
            stop(job);
        } catch(Exception error) {
            if(Thread.currentThread().isInterrupted())stop(job);
            else try{if(jobs.get(job.id()).cancelRequested())jobs.finish(job,"CANCELLED",null);else jobs.finish(job,"FAILED",safeError(error));}
                catch(RagAccessException ignored){}
        } finally {running.remove(job.id(),Thread.currentThread());heartbeats.shutdownNow();}
    }

    /** A cancelled empty revision must not hide the latest checkpoint for each other page. */
    private Map<Integer,IngestionJobRepository.Page> historicalPages(IngestionJobRepository.Job job) {
        var latest=new LinkedHashMap<Integer,IngestionJobRepository.Page>();
        var revisions=db.queryForList("SELECT DISTINCT parse_revision FROM native_rag_page WHERE document_id=? AND document_version=? AND parse_revision<? ORDER BY parse_revision DESC",
                Integer.class,job.documentId(),job.documentVersion(),job.parseRevision());
        for(int revision:revisions)for(var page:jobs.pages(job.documentId(),job.documentVersion(),revision))
            latest.putIfAbsent(page.unitNo(),page);
        return latest;
    }

    /** Saved page text replaces recognition only in the revision that an explicit update assigned it to. */
    private IngestionJobRepository.Page manualPage(IngestionJobRepository.Job job,IngestionJobRepository.Page prior,int unit) {
        if(prior==null)return null;
        var edited=db.queryForList("SELECT edited_text FROM native_rag_page_edit WHERE document_id=? AND document_version=? AND page_no=? AND applied_revision=? ORDER BY id DESC LIMIT 1",
                String.class,job.documentId(),job.documentVersion(),unit,job.parseRevision());
        if(edited.isEmpty())return null;
        Map<String,Object> location;
        try{location=new LinkedHashMap<>(json.readValue(prior.locationJson(),Map.class));}catch(Exception unreadable){location=new LinkedHashMap<>();location.put("unitNo",unit);}
        return page(unit,prior.rawText(),prior.ocrRawText(),prior.ocrText(),edited.getFirst(),"MANUAL","TEXT_READY",null,null,false,location,null);
    }

    private void stop(IngestionJobRepository.Job job) {
        boolean interrupted=Thread.interrupted();
        try{if(jobs.get(job.id()).cancelRequested())jobs.finish(job,"CANCELLED",null);}
        catch(RagAccessException ignored){}
        finally{if(interrupted)Thread.currentThread().interrupt();}
        // Shutdown without cancellation leaves the durable lease for restart recovery.
    }

    private IngestionJobRepository.Page processPage(IngestionJobRepository.Job job,RagPageExtractor.Document source,
            Map<String,Object> original,int unit,int total,int processed,int failed) {
        String nativeText="";Map<String,Object> location=new LinkedHashMap<>();location.put("unitNo",unit);
        try {
            var raw=source.page(unit);nativeText=raw.text();
            location.put("type",source.locationType());location.put("pageNumber",raw.pageNumber());
            location.put("charStart",raw.charStart());location.put("charEnd",raw.charEnd());location.put("imageCoverage",raw.imageCoverage());
            boolean force=job.forceOcrPages().contains(unit);
            var assessment=analysis.analyze(nativeText,raw.hasImages(),raw.imageCoverage(),force);
            if(raw.formulaHint())assessment=new PageAnalysisService.Assessment(true,false,"FORMULA_TEXT_LAYER");
            location.put("formulaFontHint",raw.formulaHint());
            if(assessment.blank())return page(unit,nativeText,null,null,"","BLANK","BLANK",null,null,false,location,null);
            if(!assessment.needsOcr() || source.pageCount()==0 && "FORMULA_TEXT_LAYER".equals(assessment.reason()))
                return page(unit,nativeText,null,null,nativeText,"TEXT","TEXT_READY",null,null,false,location,null);
            advance(job,"OCR",Math.min(79,5+processed*75/total),total,processed,failed);
            byte[] image=source.renderPng(unit);
            if(rasterChecks.isVisuallyBlank(image))return page(unit,nativeText,null,null,"","BLANK","BLANK",null,null,false,location,null);
            String cache=hash((text(original,"sha256")+"\n"+unit+"\n"+ocr.fingerprint()+"\nformula-v"+FormulaText.VERSION+"\n"+hash(image)).getBytes(StandardCharsets.UTF_8));
            if(!force) {
                var cached=db.queryForList("SELECT parse_revision FROM native_rag_page WHERE document_id=? AND document_version=? AND page_no=? AND ocr_fingerprint=? AND page_status='OCR_READY' ORDER BY parse_revision DESC LIMIT 1",job.documentId(),job.documentVersion(),unit,cache);
                if(!cached.isEmpty())for(var previous:jobs.pages(job.documentId(),job.documentVersion(),number(cached.getFirst(),"parse_revision")))
                    if(previous.unitNo()==unit)return previous;
            }
            boolean documentMode=raw.formulaHint() || FormulaText.suspectMath(nativeText) || !FormulaText.analyze(nativeText).blocks().isEmpty();
            var recognized=ocr.recognize(new OcrRequest(image,"image/png",raw.pageNumber(),"zh,en",job.id()+":"+unit,documentMode));
            // A diagram-only first pass can reveal math absent from the native text layer.
            if(!documentMode && ("OCR_OUTPUT_TRUNCATED".equals(recognized.errorCode()) ||
                    recognized.errorCode()==null && FormulaText.suspectMath(recognized.text())
                    && FormulaText.analyze(recognized.text()).blocks().isEmpty())) {
                authorizeJob(job);
                location.put("initialOcrMode","IMAGE_TEXT");location.put("initialOcrRawText",recognized.rawText());
                location.put("initialOcrErrorCode",recognized.errorCode());
                location.put("initialOcrLatencyMs",recognized.latencyMs());
                recognized=ocr.recognize(new OcrRequest(image,"image/png",raw.pageNumber(),"zh,en",job.id()+":"+unit+":formula",true));
                documentMode=true;
            }
            authorizeJob(job);
            location.put("ocrModel",recognized.model());location.put("latencyMs",recognized.latencyMs());
            location.put("ocrMode",documentMode?"DOCUMENT_MARKDOWN":"IMAGE_TEXT");
            if(recognized.priorRawText()!=null) {
                location.put("resolutionRetryRawText",recognized.priorRawText());
                location.put("resolutionRetryScale",recognized.retryScale());
                location.put("resolutionRetryReason","OCR_OUTPUT_TRUNCATED");
            }
            if(recognized.errorCode()!=null)
                if("OCR_EMPTY_RESULT".equals(recognized.errorCode()) &&
                        !new com.cupk.aiacademy.rag.visual.OcrLayoutParser().parse(recognized.rawText()).regions().isEmpty()) {
                    String reference="[图像来源信息，非OCR正文] "+text(original,"file_name")+"；第"+unit+"页。该页包含图像，请读取所附原图后回答。";
                    return page(unit,nativeText,recognized.rawText(),"",reference,"VISUAL_REFERENCE","OCR_READY",null,null,true,location,cache);
                } else
                return page(unit,nativeText,recognized.rawText(),recognized.text(),"","OCR","FAILED",
                        recognized.confidence(),recognized.errorCode(),true,location,null);
            String normalized=normalizer.normalize(recognized.text()).text();
            if(normalized.isBlank())throw new ModelInvocationException("OCR_EMPTY_RESULT");
            // Math without LaTeX structure is still readable text; the warning stays on this page.
            String formulaWarning=FormulaText.analyze(normalized).blocks().isEmpty() &&
                    ("FORMULA_TEXT_LAYER".equals(assessment.reason()) || FormulaText.suspectMath(normalized))?"FORMULA_STRUCTURE_MISSING":null;
            return page(unit,nativeText,recognized.rawText(),normalized,normalizer.merge(nativeText,normalized),"OCR","OCR_READY",
                    recognized.confidence(),formulaWarning,false,location,cache);
        } catch(Stopped stopped){throw stopped;}
        catch(Exception error){
            if(Thread.currentThread().isInterrupted())throw new Stopped();
            if(error instanceof RagAccessException access && Set.of("401","403").contains(access.code()))throw access;
            return page(unit,nativeText,null,null,"","OCR_OR_PARSE","FAILED",null,safeError(error),true,location,null);
        }
    }

    private void publish(IngestionJobRepository.Job job,RagPageExtractor.Document source,Map<String,Object> original,
                         List<IngestionJobRepository.Page> pages,int failed) {
        authorizeJob(job);advance(job,"CHUNKING",82,source.unitCount(),pages.size(),failed);
        List<RagTextChunker.Chunk> chunks=new ArrayList<>();
        for(var page:pages)if(Set.of("TEXT_READY","OCR_READY").contains(page.state()) && page.chosenText()!=null && !page.chosenText().isBlank())
            chunks.addAll(chunker.chunks(page.chosenText(),source.pageCount()==0?null:page.unitNo(),chunks.size()+1));
        if(chunks.isEmpty()){jobs.finish(job,"FAILED",pages.stream().map(IngestionJobRepository.Page::errorCode).filter(Objects::nonNull).findFirst().orElse("NO_RETRIEVABLE_TEXT"));return;}
        List<double[]> vectors=List.of();String embeddingState="not_configured";
        if(embeddings.isConfigured()) {
            advance(job,"EMBEDDING",88,source.unitCount(),pages.size(),failed);
            try {vectors=embeddings.embed(chunks.stream().map(RagTextChunker.Chunk::content).toList());validateVectors(vectors,chunks.size());embeddingState="indexed";}
            catch(IllegalStateException error){vectors=List.of();embeddingState="provider_unavailable";}
        } else if(embeddings.isRequested())embeddingState="configuration_incomplete";
        authorizeJob(job);List<double[]> readyVectors=vectors;String vectorState=embeddingState;
        transaction.executeWithoutResult(status->{
            if(!jobs.ownsLease(job))throw new Stopped();authorizeJob(job);
            var current=lockedDocument(job.documentId());
            if(!job.id().equals(current.get("processing_job_id")) || number(current,"document_version")!=job.expectedDocumentVersion()
                    || !Objects.equals(current.get("active_index_version"),job.expectedActiveIndex()))throw failure("409","资料或索引已改变");
            db.update("UPDATE native_rag_document SET title=?,file_name=?,file_key=?,sha256=?,mime_type=?,file_size=?,page_count=?,chunk_count=?,embedding_status=?,document_version=?,parse_revision=? WHERE id=?",
                    original.get("file_name"),original.get("file_name"),original.get("file_key"),original.get("sha256"),original.get("mime_type"),original.get("file_size"),source.pageCount(),chunks.size(),vectorState,job.documentVersion(),job.parseRevision(),job.documentId());
            String index=indexes.createStaging(job.documentId(),job.documentVersion(),job.parseRevision(),"native-formula-chunker-v3",
                    readyVectors.isEmpty()?null:embeddings.fingerprint(),readyVectors.isEmpty()?null:embeddings.modelName(),readyVectors.isEmpty()?0:readyVectors.getFirst().length,"native-page-parser-formula1-visual1");
            for(int i=0;i<chunks.size();i++) {
                var chunk=chunks.get(i);String vector=readyVectors.isEmpty()?null:write(readyVectors.get(i));
                db.update("INSERT INTO native_rag_chunk(id,document_id,kb_id,page_number,ordinal_number,content,vector_json,vector_model,index_version,document_version,parse_revision,source_type,char_start,char_end) VALUES(?,?,?,?,?,?,?,?,?,?,?,'DOCUMENT',?,?)",
                        UUID.randomUUID().toString(),job.documentId(),current.get("kb_id"),chunk.page(),chunk.ordinal(),chunk.content(),vector,
                        vector==null?null:embeddings.fingerprint(),index,job.documentVersion(),job.parseRevision(),chunk.charStart(),chunk.charEnd());
            }
            indexes.activate(job.documentId(),index,job.expectedActiveIndex(),readyVectors.isEmpty()?"KEYWORD_READY":"HYBRID_READY");
            db.update("UPDATE native_rag_document SET processing_state=? WHERE id=? AND processing_job_id=?",failed==0?"READY":"PARTIAL",job.documentId(),job.id());
            if(!jobs.finish(job,"COMPLETED",null))throw new Stopped();
        });
    }

    private void authorizeJob(IngestionJobRepository.Job job) {
        if(!jobs.ownsLease(job))throw new Stopped();var actor=auth.actorForUser(job.actorId());
        var current=document(job.documentId());ownedBase(text(current,"kb_id"),actor);
        if(!job.id().equals(current.get("processing_job_id")))throw new Stopped();
    }
    private void advance(IngestionJobRepository.Job job,String stage,int progress,int total,int processed,int failed) {
        transaction.executeWithoutResult(status->{
            if(!jobs.advance(job,stage,progress,total,processed,failed))throw new Stopped();
            db.update("UPDATE native_rag_document SET processing_state=? WHERE id=? AND processing_job_id=?",stage,job.documentId(),job.id());
        });
    }
    private Map<String,Object> uploadView(Map<String,Object> document,boolean duplicate) {
        Map<String,Object> view=new LinkedHashMap<>();
        for(String key:List.of("id","title","file_name","page_count","chunk_count","document_version","parse_revision","active_index_version","processing_state","processing_version","embedding_status"))view.put(key,document.get(key));
        view.put("documentId",document.get("id"));view.put("jobId",document.get("processing_job_id"));view.put("duplicate",duplicate);
        view.put("parse_status",text(document,"processing_state").toLowerCase(Locale.ROOT));return view;
    }
    private Map<String,Object> jobView(IngestionJobRepository.Job job) {
        var document=document(job.documentId());var pages=jobs.pages(job.documentId(),job.documentVersion(),job.parseRevision());
        var view=new LinkedHashMap<String,Object>();
        view.put("id",job.id());view.put("jobId",job.id());view.put("stage",job.stage());view.put("status",job.stage());
        view.put("documentId",job.documentId());view.put("documentVersion",job.documentVersion());view.put("parseRevision",job.parseRevision());
        view.put("progress",job.progress());view.put("totalPages",job.totalPages());view.put("completedPages",job.processedPages()-job.failedPages());
        view.put("failedPages",pages.stream().filter(p->"FAILED".equals(p.state())).map(IngestionJobRepository.Page::unitNo).toList());
        view.put("needsReviewPages",pages.stream().filter(IngestionJobRepository.Page::needsReview).map(IngestionJobRepository.Page::unitNo).toList());
        view.put("cancelRequested",job.cancelRequested());view.put("errorCode",job.errorCode());view.put("documentState",document.get("processing_state"));return view;
    }
    private Map<String,Object> pageView(IngestionJobRepository.Page page,String documentId,int version,int revision) {
        var view=new LinkedHashMap<String,Object>();view.put("pageNo",page.unitNo());view.put("pageStatus",page.state());
        view.put("rawText",page.rawText());view.put("ocrRawText",page.ocrRawText());view.put("ocrText",page.ocrText());view.put("chosenText",page.chosenText());
        view.put("method",page.method());view.put("confidence",page.confidence());view.put("errorCode",page.errorCode());view.put("needsReview",page.needsReview());
        view.put("formulaVersion",0);view.put("formulas",List.of());view.put("formulaIssues",List.of());
        if(page.formulaJson()!=null)try {
            var math=json.readValue(page.formulaJson(),Map.class);
            view.put("formulaVersion",math.get("version"));view.put("formulas",math.get("blocks"));
            view.put("formulaIssues",math.get("issues"));view.put("formulaSource",math.get("source"));
        } catch(Exception ignored){view.put("formulaIssues",List.of("FORMULA_METADATA_UNAVAILABLE"));}
        try {var location=json.readValue(page.locationJson(),Map.class);view.put("location",location);view.put("pageNumber",location.get("pageNumber"));}
        catch(Exception ignored){view.put("location",Map.of());view.put("pageNumber",null);}
        view.putAll(visuals.pageView(documentId,version,revision,page.unitNo()));return view;
    }
    private IngestionJobRepository.Page page(int unit,String raw,String originalOcr,String normalized,String chosen,String method,String state,
            Double confidence,String error,boolean review,Map<String,Object> location,String fingerprint) {
        String basis=chosen!=null&&!chosen.isBlank()?chosen:normalized!=null?normalized:raw;
        var math=FormulaText.analyze(basis);var issues=new ArrayList<>(math.issues());
        // Formula problems are local: the page text stays searchable and only the located issue asks for review.
        if(Set.of("TEXT_READY","OCR_READY").contains(state) && error!=null && error.startsWith("FORMULA_")){issues.add(error);error=null;}
        if(!issues.isEmpty())review=true;
        var metadata=new LinkedHashMap<String,Object>();metadata.put("version",FormulaText.VERSION);
        metadata.put("source",normalized==null?"TEXT":"OCR");metadata.put("blocks",math.blocks());metadata.put("issues",issues);
        return new IngestionJobRepository.Page(unit,raw,originalOcr,normalized,chosen,method,state,confidence,error,review,write(location),fingerprint,write(metadata));
    }
    private void ensureIdle(Map<String,Object> document) {
        String job=nullable(document,"processing_job_id");
        if(job!=null && !TERMINAL.contains(jobs.get(job).stage()))throw failure("409","资料仍在处理中，请等待或取消当前任务");
    }
    private Map<String,Object> ownedDocument(String id){var document=document(id);ownedBase(text(document,"kb_id"),auth.currentActor());return document;}
    private void ownedBase(String id,RagActor actor) {
        var rows=db.queryForList("SELECT owner_id FROM native_rag_base WHERE id=?",checkedId(id));
        if(rows.isEmpty())throw failure("404","知识库不存在");auth.requireBaseOwner(actor,number(rows.getFirst(),"owner_id"));
    }
    private Map<String,Object> document(String id){var rows=db.queryForList("SELECT * FROM native_rag_document WHERE id=?",checkedId(id));if(rows.isEmpty())throw failure("404","文档不存在");return rows.getFirst();}
    private Map<String,Object> lockedDocument(String id){var rows=db.queryForList("SELECT * FROM native_rag_document WHERE id=? FOR UPDATE",checkedId(id));if(rows.isEmpty())throw failure("404","文档不存在");return rows.getFirst();}
    private Map<String,Object> version(String id,int version){var rows=db.queryForList("SELECT * FROM native_rag_document_version WHERE document_id=? AND document_version=?",checkedId(id),version);if(rows.isEmpty())throw failure("404","原件版本不存在");return rows.getFirst();}
    private byte[] originalBytes(Map<String,Object> source){try{return Files.readAllBytes(files.verified(text(source,"file_key"),((Number)source.get("file_size")).longValue(),text(source,"sha256")));}catch(java.io.IOException error){throw failure("500","原件读取失败");}}
    private int latestRevision(String id,int version){return db.queryForObject("SELECT COALESCE(MAX(parse_revision),1) FROM native_rag_ingestion_job WHERE document_id=? AND document_version=?",Integer.class,id,version);}
    private int processingVersion(Map<String,Object> document){return document.get("processing_version") instanceof Number value?value.intValue():number(document,"document_version");}
    private void discard(String key) {
        if(db.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE file_key=?",Integer.class,key)>0)return;
        db.update("UPDATE native_rag_file_cleanup SET state='PENDING' WHERE file_key=?",key);
        try {files.delete(key);db.update("UPDATE native_rag_file_cleanup SET state='COMPLETED',attempts=attempts+1 WHERE file_key=?",key);}
        catch(RagAccessException failure){db.update("UPDATE native_rag_file_cleanup SET attempts=attempts+1,last_error='FILE_DELETE_FAILED' WHERE file_key=?",key);}
    }
    private void validateVectors(List<double[]> vectors,int count){
        if(vectors==null || vectors.size()!=count || count==0)throw new IllegalStateException("Incomplete embeddings");int size=vectors.getFirst().length;
        if(size<1 || size>4096)throw new IllegalStateException("Invalid dimensions");
        for(var vector:vectors){if(vector==null || vector.length!=size)throw new IllegalStateException("Inconsistent vectors");double norm=0;for(double value:vector){if(!Double.isFinite(value))throw new IllegalStateException("Invalid vector");norm+=value*value;}if(!Double.isFinite(norm)||norm<=0)throw new IllegalStateException("Invalid norm");}
    }
    private List<Integer> checkedPages(List<Integer> pages,int count){if(pages==null)return List.of();if(pages.stream().anyMatch(p->p==null || p<1 || p>count))throw failure("400","重试页码不在原文件范围内");return pages.stream().distinct().sorted().toList();}
    private String safeError(Exception error){
        if(error instanceof ModelConfigurationException config)return "MODEL_NOT_CONFIGURED".equals(config.code())?"OCR_NOT_CONFIGURED":config.code();
        if(error instanceof ModelInvocationException model)return model.code();
        if(error instanceof RagAccessException access)return Set.of("401","403").contains(access.code())?"ACTOR_ACCESS_REVOKED":"SOURCE_PARSE_FAILED";
        return "INGESTION_FAILED";
    }
    private Result result(Supplier<?> operation){try{return Result.success(operation.get());}catch(RagAccessException error){return Result.error(error.code(),error.getMessage());}catch(Exception error){return Result.error("500","资料处理失败，请检查文件及任务状态");}}
    private String safeFilename(String name){if(name==null)throw failure("400","文件名不能为空");name=name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","").strip();if(name.isBlank()||name.length()>255)throw failure("400","文件名无效");return name;}
    private String checkedId(String id){try{if(id==null || !UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();return id;}catch(IllegalArgumentException error){throw failure("400","资源ID无效");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw new IllegalStateException("Cannot persist ingestion metadata");}}
    private String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception error){throw new IllegalStateException("Cannot identify source");}}
    private int number(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}
    private String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":value.toString();}
    private String nullable(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private RagAccessException failure(String code,String message){return new RagAccessException(code,message);}
    private static class Stopped extends RuntimeException { }
}
