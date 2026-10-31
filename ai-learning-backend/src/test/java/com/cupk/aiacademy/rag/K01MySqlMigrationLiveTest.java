package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.rag.ingestion.*;
import com.cupk.aiacademy.rag.ocr.OcrProvider;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Explicit local-MySQL check. Never selects DB_URL or a pre-existing application schema. */
@EnabledIfEnvironmentVariable(named="K01_MYSQL_CHECK",matches="true")
class K01MySqlMigrationLiveTest {
    private static final String SERVER="jdbc:mysql://127.0.0.1:3306/";
    private static final String OPTIONS="?allowPublicKeyRetrieval=true&useSSL=false&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=10000";
    @TempDir Path temporary;

    @Test void isolatedMySqlPreservesLegacyDataVersionsAndOriginalsAcrossMigrationAndDeletion() throws Exception {
        String username=System.getenv("DB_USERNAME"),password=System.getenv("DB_PASSWORD");
        assertNotNull(username,"Set DB_USERNAME in this process for the explicitly enabled isolated test");
        assertNotNull(password,"Set DB_PASSWORD in this process for the explicitly enabled isolated test");
        String schema="codex_k01_"+UUID.randomUUID().toString().replace("-","");
        String ownership=UUID.randomUUID().toString();
        requireSafeSchema(schema);
        JdbcTemplate server=jdbc(SERVER+OPTIONS,username,password);
        JdbcTemplate isolated=jdbc(SERVER+schema+OPTIONS,username,password);
        var report=new LinkedHashMap<String,Object>();
        report.put("schema",schema);report.put("server","127.0.0.1:3306");
        report.put("status","FAIL");report.put("input","synthetic text/vector/image fixtures only; no model API calls");
        boolean created=false;String phase="CHECK_ISOLATION";String failure=null;
        try {
            assertNull(server.queryForObject("SELECT DATABASE()",String.class));
            assertEquals(0,schemaCount(server,schema),"Generated schema must not already exist");
            requireSafeSchema(schema);
            server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");created=true;
            phase="WRITE_OWNERSHIP_MARKER";
            isolated.execute("CREATE TABLE codex_k01_test_ownership (id INTEGER PRIMARY KEY, marker VARCHAR(36) NOT NULL)");
            isolated.update("INSERT INTO codex_k01_test_ownership(id,marker) VALUES(1,?)",ownership);
            assertEquals(schema,isolated.queryForObject("SELECT DATABASE()",String.class));
            phase="SEED_LEGACY_SCHEMA";
            legacyTables(isolated);
            String base=UUID.randomUUID().toString(),document=UUID.randomUUID().toString(),chunk=UUID.randomUUID().toString();
            String originalKey=UUID.randomUUID().toString();
            byte[] original="Legacy gradient descent source.".getBytes(StandardCharsets.UTF_8);
            var fileStore=new RagFileStore(temporary.resolve("private-rag").toString());fileStore.initialize();fileStore.store(originalKey,original);
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
            isolated.update("INSERT INTO native_rag_base(id,owner_id,name,description,shared) VALUES(?,7,'Legacy','synthetic',TRUE)",base);
            isolated.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status) VALUES(?,?,'Legacy','legacy.txt',?,?,'text/plain',?,0,1,'indexed')",document,base,originalKey,sha,original.length);
            isolated.update("INSERT INTO native_rag_chunk(id,document_id,kb_id,page_number,ordinal_number,content,vector_json,vector_model) VALUES(?,?,?,NULL,1,'Legacy gradient descent source.','[1.0,0.0]','legacy-fingerprint')",chunk,document,base);
            var user=new AuthUtils.LoginUser();user.setUserId(7);user.setRoleCode("teacher");UserContext.set(user);
            var mapper=new ObjectMapper();
            var disabled=new RagEmbeddingClient(false,"","","",mapper);
            var service=new NativeRagService(isolated,new RagDocumentParser(),disabled,mapper,temporary.resolve("private-rag").toString());
            phase="MIGRATE_AND_REPEAT";
            service.initialize();service.initialize();
            assertEquals("longtext",isolated.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME='native_rag_chunk' AND COLUMN_NAME='vector_json'",String.class,schema));
            assertEquals("Legacy gradient descent source.",isolated.queryForObject("SELECT content FROM native_rag_chunk WHERE id=?",String.class,chunk));
            assertEquals("[1.0,0.0]",isolated.queryForObject("SELECT vector_json FROM native_rag_chunk WHERE id=?",String.class,chunk));
            assertEquals("legacy-fingerprint",isolated.queryForObject("SELECT vector_model FROM native_rag_chunk WHERE id=?",String.class,chunk));
            assertEquals(1,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=? AND document_version=1",Integer.class,document));
            assertEquals(1,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_index_version WHERE document_id=?",Integer.class,document));
            assertEquals(1,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K01_001'",Integer.class));
            assertEquals(0,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant",Integer.class));
            assertArrayEquals(original,Files.readAllBytes(service.originalFile(document).path()));
            report.put("legacyDataAndOriginalPreserved",true);report.put("repeatMigrationPreserved",true);report.put("vectorColumn","LONGTEXT");

            phase="REINDEX_4096_AND_REPLACE";
            var syntheticEmbedding=mock(RagEmbeddingClient.class);
            when(syntheticEmbedding.isConfigured()).thenReturn(true);when(syntheticEmbedding.isRequested()).thenReturn(true);
            when(syntheticEmbedding.fingerprint()).thenReturn("f".repeat(64));when(syntheticEmbedding.modelName()).thenReturn("synthetic-mysql-4096");
            when(syntheticEmbedding.embed(anyList())).thenAnswer(invocation->{
                List<String> inputs=invocation.getArgument(0);return inputs.stream().map(ignored->vector()).toList();
            });
            service=new NativeRagService(isolated,new RagDocumentParser(),syntheticEmbedding,mapper,temporary.resolve("private-rag").toString());
            service.initialize();success(service.reindexDocument(document));
            String v1=isolated.queryForObject("SELECT active_index_version FROM native_rag_document_version WHERE document_id=? AND document_version=1",String.class,document);
            String stored=isolated.queryForObject("SELECT vector_json FROM native_rag_chunk WHERE index_version=?",String.class,v1);
            assertEquals(4096,mapper.readTree(stored).size());assertTrue(stored.length()>65535);
            var replacement=success(service.replaceDocument(document,"Replacement neural network source.".getBytes(StandardCharsets.UTF_8),"replacement.txt"));
            assertEquals(2,((Number)replacement.get("document_version")).intValue());
            assertEquals(v1,isolated.queryForObject("SELECT active_index_version FROM native_rag_document_version WHERE document_id=? AND document_version=1",String.class,document));
            String v2=isolated.queryForObject("SELECT active_index_version FROM native_rag_document_version WHERE document_id=? AND document_version=2",String.class,document);
            assertNotEquals(v1,v2);assertArrayEquals(original,Files.readAllBytes(temporary.resolve("private-rag").resolve(originalKey)));
            assertEquals(2,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=?",Integer.class,document));
            report.put("storedVectorCharacters",stored.length());report.put("dimensions",4096);report.put("versionSwitchAndV1Preserved",true);

            phase="DELETE_AND_COMPENSATION";
            String scope=UUID.randomUUID().toString(),item=UUID.randomUUID().toString();
            isolated.update("INSERT INTO native_rag_binding_scope(id,course_id,scope_type,scope_id,purpose,binding_mode,revision) VALUES(?,8,'COURSE',8,'LEARNING','OVERRIDE',1)",scope);
            isolated.update("INSERT INTO native_rag_binding_item(id,scope_id,source_type,document_id,document_version) VALUES(?,?,'DOCUMENT',?,1)",item,scope,document);
            isolated.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt) VALUES(?,7,8,?,1,TRUE)",UUID.randomUUID().toString(),document);
            success(service.deleteDocument(document));
            for(String table:List.of("native_rag_document","native_rag_document_version","native_rag_chunk","native_rag_index_version","native_rag_course_grant"))
                assertEquals(0,isolated.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),table);
            assertEquals(0,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_file_cleanup WHERE state<>'COMPLETED'",Integer.class));
            assertEquals(document,isolated.queryForObject("SELECT document_id FROM native_rag_binding_item WHERE id=?",String.class,item));
            try(var remaining=Files.list(temporary.resolve("private-rag"))){assertEquals(0L,remaining.count());}
            report.put("deleteCascadeAndFileCleanup",true);report.put("invalidBindingIdentityRetained",true);

            phase="K02_ZERO_MODEL_DURABLE_QUEUE";
            var queueNow=new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            Clock queueClock=new Clock(){
                @Override public ZoneId getZone(){return ZoneOffset.UTC;}
                @Override public Clock withZone(ZoneId zone){return this;}
                @Override public Instant instant(){return queueNow.get();}
            };
            var queue=new IngestionJobRepository(isolated,queueClock);
            var permissions=mock(PermissionService.class);
            when(permissions.resolveActiveUser(anyInt())).thenAnswer(invocation->{
                int id=invocation.getArgument(0);if(id!=7)throw new ForbiddenException("Unexpected isolated test actor");
                var actor=new AuthUtils.LoginUser();actor.setUserId(7);actor.setRoleCode("teacher");return actor;
            });
            var noOcr=mock(OcrProvider.class);
            when(noOcr.recognize(org.mockito.ArgumentMatchers.any())).thenThrow(new AssertionError("Plain text queue must not call OCR"));
            var ingestion=new RagIngestionService(isolated,new RagAuthorizationService(permissions,isolated),fileStore,queue,
                    new RagPageExtractor(),new PageAnalysisService(),noOcr,new RagTextChunker(),new RagIndexVersionRepository(isolated),disabled,mapper);
            var queued=success(ingestion.submit(base,"Native MySQL queue source: loss $$L=\\frac{1}{n}\\sum_i x_i^2$$ where n is sample count.".getBytes(StandardCharsets.UTF_8),"queue.txt"));
            String queueDocument=queued.get("documentId").toString(),queueJob=queued.get("jobId").toString();
            assertEquals("QUEUED",queue.get(queueJob).stage());
            var firstLease=queue.claimNext("mysql-lease-probe",Duration.ofSeconds(30)).orElseThrow();
            assertNotNull(firstLease.leaseUntil());assertTrue(queue.ownsLease(firstLease));assertTrue(queue.heartbeat(firstLease,Duration.ofSeconds(30)));
            queueNow.updateAndGet(value->value.plusSeconds(31));
            assertFalse(queue.ownsLease(firstLease));
            var secondLease=queue.claimNext("mysql-lease-probe",Duration.ofSeconds(30)).orElseThrow();
            assertEquals(firstLease.id(),secondLease.id());assertNotEquals(firstLease.leaseToken(),secondLease.leaseToken());
            assertFalse(queue.heartbeat(firstLease,Duration.ofSeconds(30)));assertFalse(queue.finish(firstLease,"COMPLETED",null));
            assertTrue(queue.requestCancel(secondLease.id()));assertTrue(queue.finish(secondLease,"CANCELLED",null));
            var retried=success(ingestion.retry(queueDocument,List.of(),false));
            assertTrue(ingestion.runNext("mysql-text-worker"));
            assertEquals("COMPLETED",queue.get(retried.get("jobId").toString()).stage());
            assertEquals("READY",isolated.queryForObject("SELECT processing_state FROM native_rag_document WHERE id=?",String.class,queueDocument));
            assertNotNull(isolated.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,queueDocument));
            var checkpoint=queue.pages(queueDocument,1,2);assertEquals(1,checkpoint.size());assertEquals("TEXT_READY",checkpoint.getFirst().state());
            assertNull(checkpoint.getFirst().confidence());verifyNoInteractions(noOcr);
            String formulaMetadata=checkpoint.getFirst().formulaJson();
            assertEquals(1,mapper.readTree(formulaMetadata).path("blocks").size());
            new RagSchemaMigrator(isolated).migrate();
            assertEquals(formulaMetadata,queue.pages(queueDocument,1,2).getFirst().formulaJson());
            report.put("k02FormulaMetadataPersisted",true);report.put("k02FormulaMigrationIdempotent",true);
            String activeIndex=isolated.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,queueDocument);
            assertEquals("native-page-parser-formula1-visual1",isolated.queryForObject("SELECT parser_revision FROM native_rag_index_version WHERE id=?",String.class,activeIndex));
            new RagSchemaMigrator(isolated).migrate();
            assertEquals("native-page-parser-formula1-visual1",isolated.queryForObject("SELECT parser_revision FROM native_rag_index_version WHERE id=?",String.class,activeIndex));
            report.put("k03ParserProvenancePersisted",true);report.put("k03MigrationIdempotent",true);
            var image=new java.awt.image.BufferedImage(200,120,java.awt.image.BufferedImage.TYPE_INT_RGB);byte[] pixels;
            try(var out=new java.io.ByteArrayOutputStream()){javax.imageio.ImageIO.write(image,"png",out);pixels=out.toByteArray();}
            var visualService=new com.cupk.aiacademy.rag.visual.RagVisualService(isolated,new RagAuthorizationService(permissions,isolated),mapper);
            visualService.capture(queueDocument,1,2,1,pixels,"1. Synthetic diagram\n<|ref|>image<|/ref|><|det|>[[0,0,999,999]]<|/det|>","1. Synthetic diagram");
            String visualId=isolated.queryForObject("SELECT id FROM native_rag_visual WHERE document_id=?",String.class,queueDocument);
            assertArrayEquals(pixels,visualService.imageForActor(visualId,7));
            new RagSchemaMigrator(isolated).migrate();assertArrayEquals(pixels,visualService.imageForActor(visualId,7));
            report.put("k02VisualBlobPersisted",true);report.put("k02VisualMigrationIdempotent",true);
            var selected=success(ingestion.retry(queueDocument,List.of(1),false));
            assertTrue(ingestion.runNext("mysql-selected-page-worker"));
            assertEquals("COMPLETED",queue.get(selected.get("jobId").toString()).stage());
            assertEquals("TEXT_READY",queue.pages(queueDocument,1,3).getFirst().state());
            verifyNoInteractions(noOcr);report.put("k02SelectedRetry",true);
            success(service.deleteDocument(queueDocument));
            assertEquals(0,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_visual WHERE document_id=?",Integer.class,queueDocument));
            report.put("k02VisualDeleteCascade",true);
            assertEquals(0,isolated.queryForObject("SELECT COUNT(*) FROM native_rag_ingestion_job",Integer.class));
            try(var remaining=Files.list(temporary.resolve("private-rag"))){assertEquals(0L,remaining.count());}
            report.put("k02DurableQueue",true);report.put("k02LeaseTimestampMapped",true);report.put("k02ExpiredLeaseFenced",true);
            phase="COMPLETE";report.put("status","PASS");
        } catch(Throwable error) {
            failure="K01_MYSQL_"+phase+"_"+error.getClass().getSimpleName();
            report.put("failureCode",failure);
        } finally {
            UserContext.clear();
            if(created) {
                try {
                    requireSafeSchema(schema);
                    var markers=isolated.queryForList("SELECT marker FROM codex_k01_test_ownership WHERE id=1",String.class);
                    if(markers.size()!=1 || !ownership.equals(markers.getFirst())) throw new IllegalStateException("Isolation ownership marker mismatch");
                    server.execute("DROP DATABASE `"+schema+"`");
                    assertEquals(0,schemaCount(server,schema));report.put("ownedTestSchemaDropped",true);
                } catch(Throwable cleanup) {
                    report.put("ownedTestSchemaDropped",false);report.put("cleanupFailureClass",cleanup.getClass().getSimpleName());
                    if(failure==null) failure="K01_MYSQL_ISOLATED_SCHEMA_CLEANUP_REFUSED";
                    report.put("status","FAIL");
                }
            } else report.put("ownedTestSchemaDropped",false);
            report.put("phase",phase);
            Path output=Path.of("target/k01-live/mysql.json");Files.createDirectories(output.getParent());
            Files.writeString(output,new ObjectMapper().writeValueAsString(report));
            System.out.println("K01_MYSQL_RESULT "+new ObjectMapper().writeValueAsString(report));
        }
        // Do not attach a provider/driver exception cause that might contain connection credentials.
        if(failure!=null) fail(failure+"; see sanitized target/k01-live/mysql.json");
    }

    private static DriverManagerDataSource source(String url,String username,String password){
        var source=new DriverManagerDataSource();source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);source.setUsername(username);source.setPassword(password);return source;
    }
    private static JdbcTemplate jdbc(String url,String username,String password){return new JdbcTemplate(source(url,username,password));}
    private static int schemaCount(JdbcTemplate server,String schema){return server.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=?",Integer.class,schema);}
    private static void requireSafeSchema(String schema){
        if(schema==null || !schema.matches("codex_k01_[0-9a-f]{32}") || schema.equalsIgnoreCase("ai_academy")) throw new IllegalArgumentException("Unsafe test schema name");
    }
    private static Map<?,?> success(Result result){assertEquals("200",result.getCode());assertInstanceOf(Map.class,result.getData());return (Map<?,?>)result.getData();}
    private static double[] vector(){
        double[] vector=new double[4096];double norm=0;
        for(int i=0;i<vector.length;i++){vector[i]=Math.sin(i+1.0);norm+=vector[i]*vector[i];}
        norm=Math.sqrt(norm);for(int i=0;i<vector.length;i++)vector[i]/=norm;return vector;
    }
    private static void legacyTables(JdbcTemplate db){
        db.execute("CREATE TABLE native_rag_base(id VARCHAR(36) PRIMARY KEY,owner_id INTEGER NOT NULL,name VARCHAR(200) NOT NULL,description TEXT NOT NULL,shared BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE native_rag_document(id VARCHAR(36) PRIMARY KEY,kb_id VARCHAR(36) NOT NULL,title VARCHAR(255) NOT NULL,file_name VARCHAR(255) NOT NULL,file_key VARCHAR(36) NOT NULL,sha256 VARCHAR(64) NOT NULL,mime_type VARCHAR(120) NOT NULL,file_size BIGINT NOT NULL,page_count INTEGER NOT NULL,chunk_count INTEGER NOT NULL,embedding_status VARCHAR(40) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT native_rag_document_hash UNIQUE(kb_id,sha256),CONSTRAINT native_rag_document_base_fk FOREIGN KEY(kb_id) REFERENCES native_rag_base(id) ON DELETE CASCADE)");
        db.execute("CREATE TABLE native_rag_chunk(id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36) NOT NULL,kb_id VARCHAR(36) NOT NULL,page_number INTEGER,ordinal_number INTEGER NOT NULL,content TEXT NOT NULL,vector_json TEXT,vector_model VARCHAR(64),CONSTRAINT native_rag_chunk_document_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE)");
    }
}
