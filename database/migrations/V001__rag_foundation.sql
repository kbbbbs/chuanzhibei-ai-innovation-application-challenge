-- K01_001. Run through RagSchemaMigrator, which checks ADD COLUMN metadata before execution.
-- All operations preserve existing rows. Shared flags never create course grants.
CREATE TABLE IF NOT EXISTS native_rag_schema_migration (
 version VARCHAR(40) PRIMARY KEY,
 applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS native_rag_base (
 id VARCHAR(36) PRIMARY KEY, owner_id INTEGER NOT NULL, name VARCHAR(200) NOT NULL,
 description TEXT NOT NULL, shared BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS native_rag_document (
 id VARCHAR(36) PRIMARY KEY, kb_id VARCHAR(36) NOT NULL, title VARCHAR(255) NOT NULL,
 file_name VARCHAR(255) NOT NULL, file_key VARCHAR(36) NOT NULL, sha256 VARCHAR(64) NOT NULL,
 mime_type VARCHAR(120) NOT NULL, file_size BIGINT NOT NULL, page_count INTEGER NOT NULL,
 chunk_count INTEGER NOT NULL, embedding_status VARCHAR(40) NOT NULL,
 document_version INTEGER NOT NULL DEFAULT 1, parse_revision INTEGER NOT NULL DEFAULT 1,
 active_index_version VARCHAR(36), source_type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT native_rag_document_hash UNIQUE(kb_id,sha256),
 CONSTRAINT native_rag_document_base_fk FOREIGN KEY(kb_id) REFERENCES native_rag_base(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_chunk (
 id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL, kb_id VARCHAR(36) NOT NULL,
 page_number INTEGER, ordinal_number INTEGER NOT NULL, content TEXT NOT NULL,
 vector_json LONGTEXT, vector_model VARCHAR(64), index_version VARCHAR(36),
 document_version INTEGER NOT NULL DEFAULT 1, parse_revision INTEGER NOT NULL DEFAULT 1,
 source_type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT', char_start INTEGER, char_end INTEGER,
 CONSTRAINT native_rag_chunk_document_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS t_native_rag_session (
 id VARCHAR(36) PRIMARY KEY, owner_id INTEGER NOT NULL, title VARCHAR(200) NOT NULL, kb_ids TEXT NOT NULL,
 course_id INTEGER, lesson_id INTEGER, mode VARCHAR(32) NOT NULL DEFAULT 'MATERIAL', binding_revision INTEGER,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS t_native_rag_message (
 id VARCHAR(36) PRIMARY KEY, sequence_no BIGINT AUTO_INCREMENT UNIQUE,
 session_id VARCHAR(36) NOT NULL, role VARCHAR(16) NOT NULL, content MEDIUMTEXT NOT NULL,
 evidence_json MEDIUMTEXT, request_id VARCHAR(36), status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
 generation_id VARCHAR(36), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE native_rag_document ADD COLUMN document_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE native_rag_document ADD COLUMN parse_revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE native_rag_document ADD COLUMN active_index_version VARCHAR(36);
ALTER TABLE native_rag_document ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT';
ALTER TABLE native_rag_chunk ADD COLUMN index_version VARCHAR(36);
ALTER TABLE native_rag_chunk ADD COLUMN document_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE native_rag_chunk ADD COLUMN parse_revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE native_rag_chunk ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT';
ALTER TABLE native_rag_chunk ADD COLUMN char_start INTEGER;
ALTER TABLE native_rag_chunk ADD COLUMN char_end INTEGER;
ALTER TABLE t_native_rag_session ADD COLUMN course_id INTEGER;
ALTER TABLE t_native_rag_session ADD COLUMN lesson_id INTEGER;
ALTER TABLE t_native_rag_session ADD COLUMN mode VARCHAR(32) NOT NULL DEFAULT 'MATERIAL';
ALTER TABLE t_native_rag_session ADD COLUMN binding_revision INTEGER;
ALTER TABLE t_native_rag_message ADD COLUMN request_id VARCHAR(36);
ALTER TABLE t_native_rag_message ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE t_native_rag_message ADD COLUMN generation_id VARCHAR(36);
CREATE TABLE IF NOT EXISTS native_rag_document_version (
 document_id VARCHAR(36) NOT NULL, document_version INTEGER NOT NULL, file_key VARCHAR(36) NOT NULL,
 sha256 VARCHAR(64) NOT NULL, file_name VARCHAR(255) NOT NULL, mime_type VARCHAR(120) NOT NULL,
 file_size BIGINT NOT NULL, actor_id INTEGER NOT NULL, active_index_version VARCHAR(36),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(document_id,document_version),
 CONSTRAINT native_rag_version_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_page (
 id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL,
 document_version INTEGER NOT NULL, parse_revision INTEGER NOT NULL DEFAULT 1, page_no INTEGER NOT NULL,
 raw_text LONGTEXT, ocr_text LONGTEXT, chosen_text LONGTEXT, method VARCHAR(32),
 page_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED', confidence DOUBLE, location_json LONGTEXT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT native_rag_page_revision UNIQUE(document_id,document_version,parse_revision,page_no),
 CONSTRAINT native_rag_page_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_ingestion_job (
 id VARCHAR(36) PRIMARY KEY, actor_id INTEGER NOT NULL, document_id VARCHAR(36) NOT NULL,
 document_version INTEGER NOT NULL, parse_revision INTEGER NOT NULL DEFAULT 1,
 target_index_version VARCHAR(36), stage VARCHAR(32) NOT NULL DEFAULT 'QUEUED', progress INTEGER NOT NULL DEFAULT 0,
 lease_owner VARCHAR(120), lease_until TIMESTAMP, heartbeat_at TIMESTAMP,
 attempts INTEGER NOT NULL DEFAULT 0, max_attempts INTEGER NOT NULL DEFAULT 3,
 cancel_requested BOOLEAN NOT NULL DEFAULT FALSE, error_code VARCHAR(100),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT native_rag_job_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_index_version (
 id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL, document_version INTEGER NOT NULL,
 parse_revision INTEGER NOT NULL, chunk_revision VARCHAR(64) NOT NULL,
 embedding_fingerprint VARCHAR(128), embedding_model VARCHAR(200), dimensions INTEGER NOT NULL DEFAULT 0,
 normalized BOOLEAN NOT NULL DEFAULT FALSE, state VARCHAR(32) NOT NULL DEFAULT 'BUILDING',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMP,
 CONSTRAINT native_rag_index_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_binding_scope (
 id VARCHAR(36) PRIMARY KEY, course_id INTEGER NOT NULL, lesson_id INTEGER,
 scope_type VARCHAR(16) NOT NULL, scope_id INTEGER NOT NULL, purpose VARCHAR(32) NOT NULL,
 binding_mode VARCHAR(16) NOT NULL DEFAULT 'INHERIT', revision INTEGER NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT native_rag_scope_identity UNIQUE(scope_type,scope_id,purpose)
);
CREATE TABLE IF NOT EXISTS native_rag_binding_item (
 id VARCHAR(36) PRIMARY KEY, scope_id VARCHAR(36) NOT NULL, source_type VARCHAR(20) NOT NULL,
 document_id VARCHAR(36), document_version INTEGER, lesson_id INTEGER, lesson_revision INTEGER,
 ordinal_number INTEGER NOT NULL DEFAULT 1,
 CONSTRAINT native_rag_binding_scope_fk FOREIGN KEY(scope_id) REFERENCES native_rag_binding_scope(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_course_grant (
 id VARCHAR(36) PRIMARY KEY, owner_id INTEGER NOT NULL, course_id INTEGER NOT NULL,
 document_id VARCHAR(36) NOT NULL, document_version INTEGER NOT NULL,
 read_excerpt BOOLEAN NOT NULL DEFAULT FALSE, download_allowed BOOLEAN NOT NULL DEFAULT FALSE,
 revoked_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT native_rag_grant_identity UNIQUE(course_id,document_id,document_version),
 CONSTRAINT native_rag_grant_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_generation_evidence (
 id VARCHAR(36) PRIMARY KEY, generation_id VARCHAR(36) NOT NULL UNIQUE, actor_id INTEGER NOT NULL,
 business_type VARCHAR(40), business_id VARCHAR(100), mode VARCHAR(32) NOT NULL,
 retrieval_mode VARCHAR(40), query_text LONGTEXT, context_hash VARCHAR(64), context_summary LONGTEXT,
 sources_json LONGTEXT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS native_rag_file_cleanup (
 file_key VARCHAR(36) PRIMARY KEY, state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 attempts INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(1000),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
