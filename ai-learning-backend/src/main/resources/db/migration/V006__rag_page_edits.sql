-- Manual page text, saved independently of indexing. Rows are append-only so every change stays traceable.
CREATE TABLE IF NOT EXISTS native_rag_page_edit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id VARCHAR(36) NOT NULL,
 document_version INTEGER NOT NULL, page_no INTEGER NOT NULL, edited_text LONGTEXT NOT NULL,
 actor_id INTEGER NOT NULL, applied_revision INTEGER,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT native_rag_page_edit_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
