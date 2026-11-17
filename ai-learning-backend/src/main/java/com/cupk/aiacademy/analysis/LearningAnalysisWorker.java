package com.cupk.aiacademy.analysis;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name="learning-analysis.worker-enabled",havingValue="true",matchIfMissing=true)
public class LearningAnalysisWorker {
    private final LearningAnalysisReportService reports;
    private final AtomicInteger active = new AtomicInteger();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, Thread.ofVirtual().name("learning-report-",0).factory());
    public LearningAnalysisWorker(LearningAnalysisReportService reports) { this.reports = reports; }

    @Scheduled(fixedDelay=1500, initialDelay=3000)
    public void poll() {
        if (active.incrementAndGet() > 2) { active.decrementAndGet(); return; }
        try {
            executor.submit(() -> {
                try { reports.runNext(); }
                finally { active.decrementAndGet(); }
            });
        } catch (RejectedExecutionException closed) { active.decrementAndGet(); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
