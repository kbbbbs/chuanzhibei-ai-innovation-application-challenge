package com.cupk.aiacademy.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagSchemaMigratorTest {
    private JdbcTemplate db;
    private final String base=UUID.randomUUID().toString(), document=UUID.randomUUID().toString(), chunk=UUID.randomUUID().toString();

    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
    }

    @Test void freshDatabaseCreatesAllK01FoundationTablesAndTracksTheVersion() {
        new RagSchemaMigrator(db).migrate();
        for(String table:List.of("native_rag_base","native_rag_document","native_rag_chunk","native_rag_document_version",
                "native_rag_page","native_rag_ingestion_job","native_rag_index_version","native_rag_binding_scope",
                "native_rag_binding_item","native_rag_course_grant","native_rag_generation_evidence","native_rag_file_cleanup",
                "t_native_rag_session","t_native_rag_message"))
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),table);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K01_001'",Integer.class));
        new RagSchemaMigrator(db).migrate();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K01_001'",Integer.class));
    }

    @Test void legacyUpgradePreservesSourceVectorsMessagesAndNeverCreatesCourseGrants() {
        legacySchema();
        db.update("INSERT INTO native_rag_base VALUES(?,7,'Legacy','notes',TRUE,CURRENT_TIMESTAMP)",base);
        db.update("INSERT INTO native_rag_document VALUES(?,?, 'old title','notes.txt',?,'file-sha','text/plain',31,1,1,'indexed',CURRENT_TIMESTAMP)",document,base,UUID.randomUUID().toString());
        db.update("INSERT INTO native_rag_chunk VALUES(?,?,?,1,1,'unchanged source','[1.0,0.0]','legacy-fingerprint')",chunk,document,base);
        db.update("INSERT INTO t_native_rag_session(id,owner_id,title,kb_ids) VALUES('session',7,'unchanged conversation','[]')");
        db.update("INSERT INTO t_native_rag_message(id,session_id,role,content) VALUES('message','session','assistant','unchanged answer')");
        new RagSchemaMigrator(db).migrate();
        new RagSchemaMigrator(db).migrate();
        var doc=db.queryForMap("SELECT * FROM native_rag_document WHERE id=?",document);
        String active=doc.get("active_index_version").toString();
        assertEquals(1,((Number)doc.get("document_version")).intValue());
        assertEquals(1,((Number)doc.get("parse_revision")).intValue());
        assertEquals("DOCUMENT",doc.get("source_type"));
        var row=db.queryForMap("SELECT * FROM native_rag_chunk WHERE id=?",chunk);
        assertEquals("unchanged source",row.get("content"));assertEquals("[1.0,0.0]",row.get("vector_json"));
        assertEquals("legacy-fingerprint",row.get("vector_model"));assertEquals(active,row.get("index_version"));
        assertNull(row.get("char_start"));assertNull(row.get("char_end"));
        assertEquals("unchanged answer",db.queryForObject("SELECT content FROM t_native_rag_message WHERE id='message'",String.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=? AND document_version=1",Integer.class,document));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_index_version WHERE document_id=?",Integer.class,document));
        assertEquals("KEYWORD_READY",db.queryForObject("SELECT state FROM native_rag_index_version WHERE id=?",String.class,active));
        assertEquals(0,db.queryForObject("SELECT dimensions FROM native_rag_index_version WHERE id=?",Integer.class,active));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_binding_item",Integer.class));
    }

    @Test void partiallyAppliedUpgradeIsResumableWithoutDroppingExistingData() {
        legacySchema();
        db.execute("ALTER TABLE native_rag_document ADD COLUMN document_version INTEGER NOT NULL DEFAULT 1");
        new RagSchemaMigrator(db).migrate();
        new RagSchemaMigrator(db).migrate();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K01_001'",Integer.class));
        db.queryForList("SELECT document_version,parse_revision,source_type,active_index_version FROM native_rag_document");
        db.queryForList("SELECT course_id,lesson_id,mode,binding_revision FROM t_native_rag_session");
        db.queryForList("SELECT request_id,status,generation_id FROM t_native_rag_message");
    }

    @Test void explicitCourseScopeHasNonNullableUniqueIdentityAndEmptyOverrideCanPersist() {
        new RagSchemaMigrator(db).migrate();
        db.update("INSERT INTO native_rag_binding_scope(id,course_id,scope_type,scope_id,purpose,binding_mode,revision) VALUES('one',8,'COURSE',8,'LEARNING','OVERRIDE',1)");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(
                "INSERT INTO native_rag_binding_scope(id,course_id,scope_type,scope_id,purpose,binding_mode,revision) VALUES('two',8,'COURSE',8,'LEARNING','INHERIT',1)"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_binding_item WHERE scope_id='one'",Integer.class));
    }

    @Test void cleanupOutboxSurvivesDocumentRemovalAndCanBeRetried() {
        new RagSchemaMigrator(db).migrate();
        db.update("INSERT INTO native_rag_file_cleanup(file_key,state,attempts) VALUES('safe-key','PENDING',0)");
        db.update("UPDATE native_rag_file_cleanup SET attempts=attempts+1,last_error='FILE_DELETE_FAILED' WHERE file_key='safe-key'");
        assertEquals(1,db.queryForObject("SELECT attempts FROM native_rag_file_cleanup WHERE file_key='safe-key'",Integer.class));
        assertEquals("PENDING",db.queryForObject("SELECT state FROM native_rag_file_cleanup WHERE file_key='safe-key'",String.class));
    }

    @Test void completedMigrationNeverInventsAnActiveIndexForNewPendingDocuments() {
        var migrator=new RagSchemaMigrator(db);migrator.migrate();
        db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,7,'New','')",base);
        db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status) VALUES(?,?,'pending','new.pdf',?,'new-sha','application/pdf',31,1,0,'pending')",document,base,UUID.randomUUID().toString());
        migrator.migrate();
        assertNull(db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_index_version WHERE document_id=?",Integer.class,document));
    }

    @Test void deletedDocumentRevokesItsGrantButKeepsBindingIdentityForAnInvalidSourceStatus() {
        new RagSchemaMigrator(db).migrate();
        db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,7,'New','')",base);
        db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status) VALUES(?,?,'notes','new.txt',?,'new-sha','text/plain',31,0,1,'not_configured')",document,base,UUID.randomUUID().toString());
        db.update("INSERT INTO native_rag_binding_scope(id,course_id,scope_type,scope_id,purpose,binding_mode,revision) VALUES('scope',8,'COURSE',8,'LEARNING','OVERRIDE',1)");
        db.update("INSERT INTO native_rag_binding_item(id,scope_id,source_type,document_id,document_version) VALUES('item','scope','DOCUMENT',?,1)",document);
        db.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt) VALUES('grant',7,8,?,1,TRUE)",document);
        db.update("DELETE FROM native_rag_document WHERE id=?",document);
        assertEquals(document,db.queryForObject("SELECT document_id FROM native_rag_binding_item WHERE id='item'",String.class));
        assertEquals(1,db.queryForObject("SELECT document_version FROM native_rag_binding_item WHERE id='item'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant",Integer.class));
        assertEquals("OVERRIDE",db.queryForObject("SELECT binding_mode FROM native_rag_binding_scope WHERE id='scope'",String.class));
    }

    private void legacySchema() {
        db.execute("CREATE TABLE native_rag_base (id VARCHAR(36) PRIMARY KEY,owner_id INT NOT NULL,name VARCHAR(200) NOT NULL,description TEXT NOT NULL,shared BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE native_rag_document (id VARCHAR(36) PRIMARY KEY,kb_id VARCHAR(36) NOT NULL,title VARCHAR(255) NOT NULL,file_name VARCHAR(255) NOT NULL,file_key VARCHAR(36) NOT NULL,sha256 VARCHAR(64) NOT NULL,mime_type VARCHAR(120) NOT NULL,file_size BIGINT NOT NULL,page_count INT NOT NULL,chunk_count INT NOT NULL,embedding_status VARCHAR(40) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(kb_id,sha256))");
        db.execute("CREATE TABLE native_rag_chunk (id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36) NOT NULL,kb_id VARCHAR(36) NOT NULL,page_number INT,ordinal_number INT NOT NULL,content TEXT NOT NULL,vector_json LONGTEXT,vector_model VARCHAR(64))");
        db.execute("CREATE TABLE t_native_rag_session (id VARCHAR(36) PRIMARY KEY,owner_id INT NOT NULL,title VARCHAR(200) NOT NULL,kb_ids TEXT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE t_native_rag_message (id VARCHAR(36) PRIMARY KEY,sequence_no BIGINT AUTO_INCREMENT UNIQUE,session_id VARCHAR(36) NOT NULL,role VARCHAR(16) NOT NULL,content MEDIUMTEXT NOT NULL,evidence_json MEDIUMTEXT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }
}
