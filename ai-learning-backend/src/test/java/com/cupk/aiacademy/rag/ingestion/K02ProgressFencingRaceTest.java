package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ocr.OcrProvider;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The slow old worker must not overwrite a recovered worker's terminal document state. */
class K02ProgressFencingRaceTest {
    @TempDir Path temporary;

    @Test void aRecoveredWorkerCannotHaveItsReadyDocumentStateOverwrittenByAnOldProgressWriter() throws Exception {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa",""));
        var executor=Executors.newFixedThreadPool(2);
        var releaseOld=new CountDownLatch(1);
        Future<Boolean> oldFuture=null,newFuture=null;
        try {
            new RagSchemaMigrator(db).migrate();
            var clock=new ControlledClock(Instant.parse("2026-10-25T00:00:00Z"));
            var jobs=spy(new IngestionJobRepository(db,clock));
            var permissions=mock(PermissionService.class);
            when(permissions.resolveActiveUser(anyInt())).thenAnswer(invocation->{
                int id=invocation.getArgument(0);if(id!=1)throw new ForbiddenException("Unexpected identity");
                var actor=new AuthUtils.LoginUser();actor.setUserId(1);actor.setRoleCode("teacher");return actor;
            });
            var auth=new RagAuthorizationService(permissions,db);
            var files=new RagFileStore(temporary.resolve("private").toString());files.initialize();
            var ocr=mock(OcrProvider.class);
            when(ocr.recognize(any())).thenThrow(new AssertionError("Native text must not call OCR"));
            var json=new ObjectMapper();
            var service=new RagIngestionService(db,auth,files,jobs,new RagPageExtractor(),new PageAnalysisService(),ocr,
                    new RagTextChunker(),new RagIndexVersionRepository(db),new RagEmbeddingClient(false,"","","",json),json);
            String base=UUID.randomUUID().toString();
            db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,1,'fencing test','')",base);
            var teacher=new AuthUtils.LoginUser();teacher.setUserId(1);teacher.setRoleCode("teacher");UserContext.set(teacher);
            var submitted=service.submit(base,"Native source with sufficient text: gradient descent updates parameters along the negative gradient to minimize training loss.".getBytes(StandardCharsets.UTF_8),"source.txt");
            assertEquals("200",submitted.getCode());var uploaded=(Map<?,?>)submitted.getData();
            String document=uploaded.get("documentId").toString(),job=uploaded.get("jobId").toString();
            UserContext.clear();
            var oldPaused=new CountDownLatch(1);var newAttempted=new CountDownLatch(1);var newClaimed=new CountDownLatch(1);
            var pauseOnce=new AtomicBoolean();var pauseTimedOut=new AtomicBoolean();
            doAnswer(invocation->{
                boolean changed=(boolean)invocation.callRealMethod();
                IngestionJobRepository.Job lease=invocation.getArgument(0);
                if(changed && "old-worker".equals(lease.leaseOwner()) && pauseOnce.compareAndSet(false,true)) {
                    oldPaused.countDown();
                    if(!releaseOld.await(10,TimeUnit.SECONDS)){pauseTimedOut.set(true);throw new IllegalStateException("Old progress writer was not released");}
                }
                return changed;
            }).when(jobs).advance(any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt());
            doAnswer(invocation->{
                String worker=invocation.getArgument(0);if("new-worker".equals(worker))newAttempted.countDown();
                @SuppressWarnings("unchecked") Optional<IngestionJobRepository.Job> claimed=(Optional<IngestionJobRepository.Job>)invocation.callRealMethod();
                if("new-worker".equals(worker) && claimed.isPresent())newClaimed.countDown();
                return claimed;
            }).when(jobs).claimNext(anyString(),any(Duration.class));
            oldFuture=executor.submit(()->service.runNext("old-worker"));
            assertTrue(oldPaused.await(5,TimeUnit.SECONDS),"Old worker must pause after its real fenced progress update");
            clock.advance(Duration.ofHours(2));
            newFuture=executor.submit(()->service.runNext("new-worker"));
            assertTrue(newAttempted.await(5,TimeUnit.SECONDS));
            // With the bug the first transaction is already committed, so recovery can complete now.
            // With the fix the root transaction still owns the job lock; release it after a bounded latch wait.
            if(newClaimed.await(300,TimeUnit.MILLISECONDS))assertTrue(newFuture.get(5,TimeUnit.SECONDS));
            releaseOld.countDown();
            assertTrue(oldFuture.get(5,TimeUnit.SECONDS));assertTrue(newFuture.get(5,TimeUnit.SECONDS));
            assertFalse(pauseTimedOut.get(),"A timeout must never be mistaken for a successful race reproduction");
            assertEquals("COMPLETED",jobs.get(job).stage());
            assertEquals("READY",db.queryForObject("SELECT processing_state FROM native_rag_document WHERE id=?",String.class,document),
                    "Old unfenced document progress must never overwrite the recovered terminal state");
            assertNotNull(db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document));
            verifyNoInteractions(ocr);
        } finally {
            releaseOld.countDown();
            if(oldFuture!=null)oldFuture.cancel(true);if(newFuture!=null)newFuture.cancel(true);
            executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);
            UserContext.clear();db.execute("DROP ALL OBJECTS");
        }
    }

    private static final class ControlledClock extends Clock {
        private final AtomicReference<Instant> now;
        ControlledClock(Instant instant){now=new AtomicReference<>(instant);}
        void advance(Duration duration){now.updateAndGet(value->value.plus(duration));}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now.get();}
    }
}
