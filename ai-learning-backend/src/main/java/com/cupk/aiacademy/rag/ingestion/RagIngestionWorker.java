package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.common.UserContext;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** At most two documents and two rendered pages in flight; expired persistent leases can be resumed after restart. */
@Component
@ConditionalOnProperty(name="rag.ingestion.worker-enabled",havingValue="true",matchIfMissing=true)
public class RagIngestionWorker {
    private final RagIngestionService service;
    private final ExecutorService executor=Executors.newFixedThreadPool(2,Thread.ofVirtual().name("rag-ingestion-",0).factory());
    private final AtomicInteger active=new AtomicInteger();
    private final String worker=UUID.randomUUID().toString();
    public RagIngestionWorker(RagIngestionService service){this.service=service;}
    @Scheduled(fixedDelayString="${rag.ingestion.poll-delay-ms:1000}",initialDelayString="${rag.ingestion.initial-delay-ms:1500}")
    public void poll(){
        for(int slot=0;slot<2;slot++){
            if(active.incrementAndGet()>2){active.decrementAndGet();return;}
            try{executor.submit(()->{UserContext.clear();try{service.runNext(worker);}catch(RuntimeException ignored){}
                finally{UserContext.clear();active.decrementAndGet();}});}
            catch(RejectedExecutionException stopped){active.decrementAndGet();return;}
        }
    }
    @PreDestroy public void close(){executor.shutdownNow();}
}
