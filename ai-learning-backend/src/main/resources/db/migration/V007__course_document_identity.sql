-- Preserve independent document histories when legacy libraries contain the same original.
ALTER TABLE native_rag_document ADD COLUMN migration_identity VARCHAR(36) NOT NULL DEFAULT '';
