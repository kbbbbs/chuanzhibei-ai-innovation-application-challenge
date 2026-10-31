package com.cupk.aiacademy.rag;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class K02SchemaMigrationTest {
    @Test void k02MigrationAddsProcessingAndOcrStateAndRepeatsWithoutLosingK01Data() {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        var migrator=new RagSchemaMigrator(db);migrator.migrate();
        String base=UUID.randomUUID().toString(),document=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();
        db.update("INSERT INTO native_rag_base(id,owner_id,name,description,shared) VALUES(?,7,'legacy','',TRUE)",base);
        db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status,active_index_version) VALUES(?,?,'old','old.pdf',?,'old-sha','application/pdf',20,1,1,'indexed','old-index')",document,base,key);
        migrator.migrate();
        assertEquals("READY",db.queryForObject("SELECT processing_state FROM native_rag_document WHERE id=?",String.class,document));
        assertEquals("old-index",db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document));
        assertEquals("old-sha",db.queryForObject("SELECT sha256 FROM native_rag_document WHERE id=?",String.class,document));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K02_002'",Integer.class));
        db.update("UPDATE native_rag_document SET processing_state='QUEUED',processing_job_id='job',processing_version=2 WHERE id=?",document);
        migrator.migrate();
        assertEquals("QUEUED",db.queryForObject("SELECT processing_state FROM native_rag_document WHERE id=?",String.class,document));
        assertEquals("old-index",db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant",Integer.class));
    }

    @Test void versionedSqlMirrorIsExactAndFreshSchemaContainsTheSameK02Columns() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path resource=Path.of("src/main/resources/db/migration/V002__rag_ingestion.sql");
        if(!Files.exists(resource)){root=Path.of("").toAbsolutePath();resource=root.resolve("ai-learning-backend/src/main/resources/db/migration/V002__rag_ingestion.sql");}
        assertArrayEquals(Files.readAllBytes(resource),Files.readAllBytes(root.resolve("database/migrations/V002__rag_ingestion.sql")));
        String fresh=Files.readString(root.resolve("database/native_rag.sql"));
        assertArrayEquals(Files.readAllBytes(root.resolve("ai-learning-backend/src/main/resources/db/migration/V003__rag_formulas.sql")),
                Files.readAllBytes(root.resolve("database/migrations/V003__rag_formulas.sql")));
        assertTrue(fresh.contains("formula_json"));
        assertArrayEquals(Files.readAllBytes(root.resolve("ai-learning-backend/src/main/resources/db/migration/V004__rag_visuals.sql")),
                Files.readAllBytes(root.resolve("database/migrations/V004__rag_visuals.sql")));
        assertTrue(fresh.contains("native_rag_visual_page"));assertTrue(fresh.contains("image_png LONGBLOB"));
        for(String column:new String[]{"processing_state","processing_job_id","processing_version","lease_token","expected_active_index","expected_document_version","force_ocr_pages_json","selected_pages_json","total_pages","processed_pages","failed_pages","ocr_raw_text","needs_review","ocr_fingerprint"})
            assertTrue(fresh.contains(column),column);
    }
}
