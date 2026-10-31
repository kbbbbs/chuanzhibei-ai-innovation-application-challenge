-- K02_004. Private, versioned image assets. Never public uploads or model data URLs.
CREATE TABLE IF NOT EXISTS native_rag_visual_page (
 document_id VARCHAR(36) NOT NULL, document_version INTEGER NOT NULL,
 parse_revision INTEGER NOT NULL, page_no INTEGER NOT NULL, state_json LONGTEXT NOT NULL,
 PRIMARY KEY(document_id,document_version,parse_revision,page_no),
 CONSTRAINT native_rag_visual_page_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS native_rag_visual (
 id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL,
 document_version INTEGER NOT NULL, parse_revision INTEGER NOT NULL, page_no INTEGER NOT NULL,
 ordinal_number INTEGER NOT NULL, metadata_json LONGTEXT NOT NULL,
 anchor_start INTEGER, anchor_end INTEGER, image_png LONGBLOB NOT NULL,
 image_sha256 VARCHAR(64) NOT NULL, byte_size INTEGER NOT NULL,
 CONSTRAINT native_rag_visual_doc_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE,
 CONSTRAINT native_rag_visual_page_order UNIQUE(document_id,document_version,parse_revision,page_no,ordinal_number)
);
