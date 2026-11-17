package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.RagAccessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Persistent queue and fenced page writes. Callers authorize the actor and publish the index in the same transaction. */
@Repository
public class IngestionJobRepository {
    private static final Set<String> ACTIVE=Set.of("PARSING","OCR","CHUNKING","EMBEDDING");
    private static final Set<String> TERMINAL=Set.of("COMPLETED","FAILED","CANCELLED");
    private static final Set<String> PAGE_STATES=Set.of("TEXT_READY","OCR_READY","BLANK","FAILED");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ObjectMapper json=new ObjectMapper();

    public record Job(String id,int actorId,String documentId,int documentVersion,int parseRevision,String stage,
                      int progress,int attempts,boolean cancelRequested,String leaseOwner,String leaseToken,
                      Instant leaseUntil,String expectedActiveIndex,int expectedDocumentVersion,
                      List<Integer> forceOcrPages,List<Integer> selectedPages,int totalPages,int processedPages,
                      int failedPages,String errorCode) { }
    public record Page(int unitNo,String rawText,String ocrRawText,String ocrText,String chosenText,String method,
                       String state,Double confidence,String errorCode,boolean needsReview,String locationJson,
                       String ocrFingerprint,String formulaJson) {
        public Page(int unitNo,String rawText,String ocrRawText,String ocrText,String chosenText,String method,
                    String state,Double confidence,String errorCode,boolean needsReview,String locationJson,String ocrFingerprint) {
            this(unitNo,rawText,ocrRawText,ocrText,chosenText,method,state,confidence,errorCode,needsReview,locationJson,ocrFingerprint,null);
        }
        public int pageNo(){return unitNo;}
    }

    @Autowired public IngestionJobRepository(JdbcTemplate jdbc){this(jdbc,Clock.systemUTC());}
    public IngestionJobRepository(JdbcTemplate jdbc,Clock clock) {
        this.jdbc=Objects.requireNonNull(jdbc);this.clock=Objects.requireNonNull(clock);
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public String enqueue(int actorId,String documentId,int documentVersion,int parseRevision,
                          String expectedActiveIndex,int expectedDocumentVersion,List<Integer> forceOcrPages,
                          List<Integer> selectedPages) {
        if(actorId<=0 || documentVersion<1 || parseRevision<1 || expectedDocumentVersion<1)
            throw new IllegalArgumentException("Invalid ingestion identity or revision");
        List<Integer> force=pageNumbers(forceOcrPages),selected=pageNumbers(selectedPages);
        return transaction.execute(status->{
            var documents=jdbc.queryForList("SELECT id FROM native_rag_document WHERE id=? FOR UPDATE",documentId);
            if(documents.size()!=1)throw new RagAccessException("404","文档不存在");
            var duplicates=jdbc.queryForList("SELECT id FROM native_rag_ingestion_job WHERE document_id=? AND document_version=? AND parse_revision=?",documentId,documentVersion,parseRevision);
            if(!duplicates.isEmpty())return duplicates.getFirst().get("id").toString();
            if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=? AND document_version=?",Integer.class,documentId,documentVersion)!=1)
                throw new IllegalStateException("Ingestion source version is unavailable");
            String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO native_rag_ingestion_job(id,actor_id,document_id,document_version,parse_revision,expected_active_index,expected_document_version,force_ocr_pages_json,selected_pages_json,stage,progress) VALUES(?,?,?,?,?,?,?,?,?,'QUEUED',0)",
                    id,actorId,documentId,documentVersion,parseRevision,expectedActiveIndex,expectedDocumentVersion,write(force),write(selected));
            return id;
        });
    }

    public Optional<Job> claimNext(String workerId,Duration lease) {
        requireWorker(workerId);requireDuration(lease);
        return transaction.execute(status->{
            Instant now=clock.instant();
            var candidates=jdbc.queryForList("SELECT id FROM native_rag_ingestion_job WHERE stage='QUEUED' OR (stage IN ('PARSING','OCR','CHUNKING','EMBEDDING') AND (lease_until IS NULL OR lease_until<=?)) ORDER BY created_at,id LIMIT 100",String.class,Timestamp.from(now));
            for(String id:candidates) {
                Map<String,Object> row=locked(id);
                if(row==null || !claimable(row,now))continue;
                if(bool(row.get("cancel_requested"))) {terminal(row,"CANCELLED",null);continue;}
                if(number(row,"attempts")>=number(row,"max_attempts")) {terminal(row,"FAILED","JOB_ATTEMPTS_EXHAUSTED");continue;}
                String token=UUID.randomUUID().toString();
                jdbc.update("UPDATE native_rag_ingestion_job SET stage='PARSING',lease_owner=?,lease_token=?,lease_until=?,heartbeat_at=?,attempts=attempts+1,error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        workerId,token,Timestamp.from(now.plus(lease)),Timestamp.from(now),id);
                return Optional.of(get(id));
            }
            return Optional.empty();
        });
    }

    public Job get(String id) {
        var rows=jdbc.queryForList("SELECT * FROM native_rag_ingestion_job WHERE id=?",id);
        if(rows.isEmpty())throw new RagAccessException("404","资料处理任务不存在");
        return job(rows.getFirst());
    }

    /** SELECT FOR UPDATE participates in an outer publication transaction, keeping cancel and finish ordered. */
    public boolean ownsLease(Job lease){return withLease(lease,false,row->true);}

    public boolean heartbeat(Job lease,Duration duration) {
        requireDuration(duration);
        return withLease(lease,false,row->{
            Instant now=clock.instant();
            jdbc.update("UPDATE native_rag_ingestion_job SET heartbeat_at=?,lease_until=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    Timestamp.from(now),Timestamp.from(now.plus(duration)),lease.id());return true;
        });
    }

    public boolean advance(Job lease,String stage,int progress,int totalPages,int processedPages,int failedPages) {
        if(!ACTIVE.contains(stage) || progress<0 || progress>=100 || totalPages<0 || totalPages>200
                || processedPages<0 || processedPages>totalPages || failedPages<0 || failedPages>processedPages)
            throw new IllegalArgumentException("Invalid ingestion progress");
        return withLease(lease,false,row->{
            jdbc.update("UPDATE native_rag_ingestion_job SET stage=?,progress=?,total_pages=?,processed_pages=?,failed_pages=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    stage,progress,totalPages,processedPages,failedPages,lease.id());return true;
        });
    }

    public boolean savePage(Job lease,Page page) {
        validatePage(page);
        return withLease(lease,false,row->{save(row,page);return true;});
    }

    public List<Page> pages(String documentId,int documentVersion,int parseRevision) {
        return jdbc.queryForList("SELECT * FROM native_rag_page WHERE document_id=? AND document_version=? AND parse_revision=? ORDER BY page_no",
                documentId,documentVersion,parseRevision).stream().map(this::page).toList();
    }

    public int copyReusablePages(Job lease,int sourceRevision,Set<Integer> excluded) {
        if(sourceRevision<1 || lease==null || sourceRevision>=lease.parseRevision())throw new IllegalArgumentException("Invalid source parse revision");
        Set<Integer> ignored=new HashSet<>(pageNumbers(excluded==null?List.of():new ArrayList<>(excluded)));
        Integer count=transaction.execute(status->{
            Map<String,Object> row=locked(lease.id());if(!owned(row,lease,false))return 0;
            int copied=0;
            for(Page page:pages(lease.documentId(),lease.documentVersion(),sourceRevision)) {
                if(ignored.contains(page.unitNo()) || !Set.of("TEXT_READY","OCR_READY","BLANK").contains(page.state()))continue;
                save(row,page);copied++;
            }
            return copied;
        });
        return count==null?0:count;
    }

    public boolean requestCancel(String id) {
        return transaction.execute(status->{
            Map<String,Object> row=locked(id);if(row==null || TERMINAL.contains(text(row,"stage")))return false;
            jdbc.update("UPDATE native_rag_ingestion_job SET cancel_requested=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
            if("QUEUED".equals(text(row,"stage")) || !unexpired(row,clock.instant()))terminal(row,"CANCELLED",null);
            return true;
        });
    }

    public boolean finish(Job lease,String state,String errorCode) {
        if(!TERMINAL.contains(state))throw new IllegalArgumentException("Invalid ingestion terminal state");
        String safeError=safeCode(errorCode);
        return withLease(lease,"CANCELLED".equals(state),row->{terminal(row,state,safeError);return true;});
    }

    private boolean withLease(Job lease,boolean allowCancel,Function<Map<String,Object>,Boolean> action) {
        if(lease==null)return false;
        return Boolean.TRUE.equals(transaction.execute(status->{
            Map<String,Object> row=locked(lease.id());
            if(!owned(row,lease,allowCancel))return false;
            return action.apply(row);
        }));
    }
    private Map<String,Object> locked(String id) {
        var rows=jdbc.queryForList("SELECT * FROM native_rag_ingestion_job WHERE id=? FOR UPDATE",id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private boolean owned(Map<String,Object> row,Job lease,boolean allowCancel) {
        return row!=null && ACTIVE.contains(text(row,"stage")) && unexpired(row,clock.instant())
                && Objects.equals(row.get("lease_owner"),lease.leaseOwner()) && lease.leaseToken()!=null
                && Objects.equals(row.get("lease_token"),lease.leaseToken())
                && (allowCancel || !bool(row.get("cancel_requested")));
    }
    private boolean claimable(Map<String,Object> row,Instant now) {
        return "QUEUED".equals(text(row,"stage")) || ACTIVE.contains(text(row,"stage")) && !unexpired(row,now);
    }
    private boolean unexpired(Map<String,Object> row,Instant now) {
        Object expiry=row.get("lease_until");return expiry instanceof Timestamp timestamp && timestamp.toInstant().isAfter(now);
    }
    private void terminal(Map<String,Object> row,String state,String errorCode) {
        jdbc.update("UPDATE native_rag_ingestion_job SET stage=?,progress=?,error_code=?,lease_owner=NULL,lease_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                state,"COMPLETED".equals(state)?100:number(row,"progress"),errorCode,text(row,"id"));
        if(!"COMPLETED".equals(state))jdbc.update("UPDATE native_rag_document SET processing_state=? WHERE id=? AND processing_job_id=?",
                state,text(row,"document_id"),text(row,"id"));
    }
    private void save(Map<String,Object> job,Page page) {
        String state=page.state();
        int changed=jdbc.update("UPDATE native_rag_page SET raw_text=?,ocr_raw_text=?,ocr_text=?,chosen_text=?,method=?,page_status=?,confidence=?,error_code=?,needs_review=?,location_json=?,ocr_fingerprint=?,formula_json=?,updated_at=CURRENT_TIMESTAMP WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",
                page.rawText(),page.ocrRawText(),page.ocrText(),page.chosenText(),page.method(),state,page.confidence(),safeCode(page.errorCode()),page.needsReview(),page.locationJson(),page.ocrFingerprint(),page.formulaJson(),
                job.get("document_id"),job.get("document_version"),job.get("parse_revision"),page.unitNo());
        if(changed==0)jdbc.update("INSERT INTO native_rag_page(id,document_id,document_version,parse_revision,page_no,raw_text,ocr_raw_text,ocr_text,chosen_text,method,page_status,confidence,error_code,needs_review,location_json,ocr_fingerprint,formula_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),job.get("document_id"),job.get("document_version"),job.get("parse_revision"),page.unitNo(),page.rawText(),page.ocrRawText(),page.ocrText(),page.chosenText(),page.method(),state,page.confidence(),safeCode(page.errorCode()),page.needsReview(),page.locationJson(),page.ocrFingerprint(),page.formulaJson());
    }
    private Job job(Map<String,Object> row) {
        return new Job(text(row,"id"),number(row,"actor_id"),text(row,"document_id"),number(row,"document_version"),number(row,"parse_revision"),text(row,"stage"),number(row,"progress"),number(row,"attempts"),bool(row.get("cancel_requested")),
                nullable(row,"lease_owner"),nullable(row,"lease_token"),row.get("lease_until") instanceof Timestamp value?value.toInstant():null,
                nullable(row,"expected_active_index"),number(row,"expected_document_version"),readPages(nullable(row,"force_ocr_pages_json")),readPages(nullable(row,"selected_pages_json")),number(row,"total_pages"),number(row,"processed_pages"),number(row,"failed_pages"),nullable(row,"error_code"));
    }
    private Page page(Map<String,Object> row) {
        return new Page(number(row,"page_no"),nullable(row,"raw_text"),nullable(row,"ocr_raw_text"),nullable(row,"ocr_text"),nullable(row,"chosen_text"),nullable(row,"method"),text(row,"page_status"),
                row.get("confidence") instanceof Number value?value.doubleValue():null,nullable(row,"error_code"),bool(row.get("needs_review")),nullable(row,"location_json"),nullable(row,"ocr_fingerprint"),nullable(row,"formula_json"));
    }
    private void validatePage(Page page) {
        if(page==null || page.unitNo()<1 || page.unitNo()>200 || !PAGE_STATES.contains(page.state())
                || page.confidence()!=null && (!Double.isFinite(page.confidence()) || page.confidence()<0 || page.confidence()>1))
            throw new IllegalArgumentException("Invalid ingestion page");
        for(String content:new String[]{page.rawText(),page.ocrRawText(),page.ocrText(),page.chosenText()})
            if(content!=null && content.length()>1_000_000)throw new IllegalArgumentException("Page text exceeds capacity");
    }
    private List<Integer> pageNumbers(List<Integer> values) {
        if(values==null)return List.of();
        if(values.size()>200 || values.stream().anyMatch(value->value==null || value<1 || value>200))throw new IllegalArgumentException("Invalid selected page range");
        return values.stream().distinct().sorted().toList();
    }
    private List<Integer> readPages(String value) {
        if(value==null || value.isBlank())return List.of();
        try{return pageNumbers(json.readValue(value,new TypeReference<List<Integer>>(){}));}
        catch(Exception ignored){throw new IllegalStateException("Invalid persisted ingestion page selection");}
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ignored){throw new IllegalStateException("Cannot encode ingestion options");}}
    private static String safeCode(String code) {
        if(code==null || code.isBlank())return null;
        return code.matches("[A-Z0-9_]{1,100}")?code:"INGESTION_FAILED";
    }
    private static void requireWorker(String worker){if(worker==null || worker.isBlank() || worker.length()>120)throw new IllegalArgumentException("Invalid lease owner");}
    private static void requireDuration(Duration duration){if(duration==null || duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofHours(1))>0)throw new IllegalArgumentException("Invalid lease duration");}
    private static int number(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Number number?number.intValue():0;}
    private static String text(Map<String,Object> row,String key){String value=nullable(row,key);return value==null?"":value;}
    private static String nullable(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private static boolean bool(Object value){return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue()!=0;}
}
