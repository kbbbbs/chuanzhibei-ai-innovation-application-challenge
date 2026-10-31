-- K02_002. Metadata-guarded incremental processing fields; apply through RagSchemaMigrator.
ALTER TABLE native_rag_document ADD COLUMN processing_state VARCHAR(32) NOT NULL DEFAULT 'READY';
ALTER TABLE native_rag_document ADD COLUMN processing_job_id VARCHAR(36);
ALTER TABLE native_rag_document ADD COLUMN processing_version INTEGER;
ALTER TABLE native_rag_ingestion_job ADD COLUMN lease_token VARCHAR(36);
ALTER TABLE native_rag_ingestion_job ADD COLUMN expected_active_index VARCHAR(36);
ALTER TABLE native_rag_ingestion_job ADD COLUMN expected_document_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE native_rag_ingestion_job ADD COLUMN force_ocr_pages_json LONGTEXT;
ALTER TABLE native_rag_ingestion_job ADD COLUMN selected_pages_json LONGTEXT;
ALTER TABLE native_rag_ingestion_job ADD COLUMN total_pages INTEGER NOT NULL DEFAULT 0;
ALTER TABLE native_rag_ingestion_job ADD COLUMN processed_pages INTEGER NOT NULL DEFAULT 0;
ALTER TABLE native_rag_ingestion_job ADD COLUMN failed_pages INTEGER NOT NULL DEFAULT 0;
ALTER TABLE native_rag_page ADD COLUMN ocr_raw_text LONGTEXT;
ALTER TABLE native_rag_page ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE native_rag_page ADD COLUMN error_code VARCHAR(100);
ALTER TABLE native_rag_page ADD COLUMN ocr_fingerprint VARCHAR(128);
