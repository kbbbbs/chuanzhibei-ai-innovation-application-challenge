package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.RagSchemaMigrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class IngestionJobRepositoryTest {
    JdbcTemplate db;
    IngestionJobRepository jobs;
    MutableClock clock;
    String base=UUID.randomUUID().toString(),document=UUID.randomUUID().toString();
    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa",""));
        new RagSchemaMigrator(db).migrate();
        db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,7,'test','')",base);
        String key=UUID.randomUUID().toString();
        db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status,active_index_version) VALUES(?,?,'source','source.pdf',?,'sha','application/pdf',20,1,1,'not_configured','prior-index')",document,base,key);
        db.update("INSERT INTO native_rag_document_version(document_id,document_version,file_key,sha256,file_name,mime_type,file_size,actor_id) VALUES(?,1,?,'sha','source.pdf','application/pdf',20,7)",document,key);
        clock=new MutableClock(Instant.parse("2026-10-25T00:00:00Z"));
        jobs=new IngestionJobRepository(db,clock);
    }
    String enqueue(){return jobs.enqueue(7,document,1,2,"prior-index",1,List.of(1),List.of());}
    IngestionJobRepository.Job claim(){enqueue();return jobs.claimNext("worker",Duration.ofSeconds(30)).orElseThrow();}
    IngestionJobRepository.Page page(int number,String state,String text){return new IngestionJobRepository.Page(number,"raw "+text,"<raw>"+text,text,text,"OCR",state,null,null,false,"{\"page\":"+number+"}","ocr-fingerprint");}

    @Test void duplicateEnqueueIsIdempotentAndDoesNotChangeTheActiveIndex() {
        String id=enqueue();assertEquals(id,enqueue());
        var job=jobs.get(id);
        assertEquals("QUEUED",job.stage());assertEquals(7,job.actorId());assertEquals(2,job.parseRevision());
        assertEquals("prior-index",job.expectedActiveIndex());assertEquals(List.of(1),job.forceOcrPages());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_ingestion_job",Integer.class));
        assertEquals("prior-index",db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document));
    }

    @Test void twoWorkersCannotClaimTheSameUnexpiredLease() throws Exception {
        enqueue();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new CountDownLatch(1);
            var first=executor.submit(()->{start.await();return jobs.claimNext("one",Duration.ofSeconds(30));});
            var second=executor.submit(()->{start.await();return jobs.claimNext("two",Duration.ofSeconds(30));});
            start.countDown();
            assertEquals(1,(first.get().isPresent()?1:0)+(second.get().isPresent()?1:0));
        }
        assertTrue(jobs.claimNext("third",Duration.ofSeconds(30)).isEmpty());
    }

    @Test void restartRecoversExpiredLeaseAndFencesThePreviousWriterEvenWithSameWorkerName() {
        var first=claim();clock.advance(Duration.ofSeconds(31));
        var recovered=new IngestionJobRepository(db,clock).claimNext("worker",Duration.ofSeconds(30)).orElseThrow();
        assertEquals(first.id(),recovered.id());assertNotEquals(first.leaseToken(),recovered.leaseToken());assertEquals(2,recovered.attempts());
        assertFalse(jobs.ownsLease(first));assertFalse(jobs.heartbeat(first,Duration.ofSeconds(30)));
        assertFalse(jobs.savePage(first,page(1,"OCR_READY","stale")));
        assertFalse(jobs.finish(first,"COMPLETED",null));
        assertTrue(jobs.savePage(recovered,page(1,"OCR_READY","winner")));
        assertEquals("winner",jobs.pages(document,1,2).getFirst().chosenText());
    }

    @Test void pageWritesAreIdempotentAndKeepUnknownConfidenceAndRawOcr() {
        var lease=claim();assertTrue(jobs.savePage(lease,page(1,"OCR_READY","first")));
        assertTrue(jobs.savePage(lease,page(1,"OCR_READY","corrected")));
        var pages=jobs.pages(document,1,2);assertEquals(1,pages.size());
        assertEquals("corrected",pages.getFirst().chosenText());assertEquals("<raw>corrected",pages.getFirst().ocrRawText());
        assertNull(pages.getFirst().confidence());assertEquals(1,pages.getFirst().unitNo());
        assertThrows(IllegalArgumentException.class,()->jobs.savePage(lease,page(201,"OCR_READY","invalid")));
    }

    @Test void cancellationPreventsPublicationAndAllowsOwnedCancellationTerminal() {
        var lease=claim();assertTrue(jobs.requestCancel(lease.id()));
        assertFalse(jobs.ownsLease(lease));assertFalse(jobs.savePage(lease,page(1,"OCR_READY","late")));
        assertFalse(jobs.finish(lease,"COMPLETED",null));
        assertTrue(jobs.finish(lease,"CANCELLED",null));assertEquals("CANCELLED",jobs.get(lease.id()).stage());
        assertTrue(jobs.claimNext("worker",Duration.ofSeconds(30)).isEmpty());
        assertEquals("prior-index",db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document));
    }

    @Test void queuedCancellationNeverClaimsAWorkerAndCompletedCannotBeCancelled() {
        String id=enqueue();assertTrue(jobs.requestCancel(id));assertEquals("CANCELLED",jobs.get(id).stage());
        assertTrue(jobs.claimNext("worker",Duration.ofSeconds(30)).isEmpty());
        String second=jobs.enqueue(7,document,1,3,"prior-index",1,List.of(),List.of());
        var lease=jobs.claimNext("worker",Duration.ofSeconds(30)).orElseThrow();assertEquals(second,lease.id());
        assertTrue(jobs.finish(lease,"COMPLETED",null));assertFalse(jobs.requestCancel(second));
        assertEquals(100,jobs.get(second).progress());
    }

    @Test void heartbeatExtendsTheLeaseAndStateProgressIsPersisted() {
        var lease=claim();clock.advance(Duration.ofSeconds(20));
        assertTrue(jobs.heartbeat(lease,Duration.ofSeconds(30)));clock.advance(Duration.ofSeconds(20));
        assertTrue(jobs.ownsLease(lease));
        assertTrue(jobs.advance(lease,"OCR",40,3,1,0));
        var current=jobs.get(lease.id());assertEquals("OCR",current.stage());assertEquals(40,current.progress());assertEquals(3,current.totalPages());assertEquals(1,current.processedPages());
        assertThrows(IllegalArgumentException.class,()->jobs.advance(lease,"OCR",101,3,1,0));
    }

    @Test void exhaustedLeaseFailsRatherThanBeingReclaimedForever() {
        var lease=claim();db.update("UPDATE native_rag_ingestion_job SET max_attempts=1 WHERE id=?",lease.id());
        clock.advance(Duration.ofSeconds(31));assertTrue(jobs.claimNext("new",Duration.ofSeconds(30)).isEmpty());
        assertEquals("FAILED",jobs.get(lease.id()).stage());assertEquals("JOB_ATTEMPTS_EXHAUSTED",jobs.get(lease.id()).errorCode());
    }

    @Test void selectiveRetryCopiesOnlyReusableUnselectedPagesIntoANewRevision() {
        var first=claim();
        jobs.savePage(first,page(1,"OCR_READY","reuse"));jobs.savePage(first,page(2,"FAILED",""));jobs.savePage(first,page(3,"TEXT_READY","force again"));
        assertTrue(jobs.finish(first,"FAILED","OCR_PAGE_FAILED"));
        String next=jobs.enqueue(7,document,1,3,"prior-index",1,List.of(2,3),List.of(2,3));
        var retry=jobs.claimNext("worker",Duration.ofSeconds(30)).orElseThrow();assertEquals(next,retry.id());
        assertEquals(1,jobs.copyReusablePages(retry,2,Set.of(2,3)));assertEquals(1,jobs.copyReusablePages(retry,2,Set.of(2,3)));
        assertEquals(List.of(1),jobs.pages(document,1,3).stream().map(IngestionJobRepository.Page::unitNo).toList());
        assertEquals(3,jobs.pages(document,1,2).size());
    }

    @Test void aCancelledExpiredLeaseIsTerminatedWithoutReprocessing() {
        var lease=claim();jobs.requestCancel(lease.id());clock.advance(Duration.ofSeconds(31));
        assertTrue(jobs.claimNext("new",Duration.ofSeconds(30)).isEmpty());assertEquals("CANCELLED",jobs.get(lease.id()).stage());
    }

    static class MutableClock extends Clock {
        private Instant instant;MutableClock(Instant instant){this.instant=instant;}
        void advance(Duration duration){instant=instant.plus(duration);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return instant;}
    }
}
