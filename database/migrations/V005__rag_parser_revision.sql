-- K03: preserve actual parser algorithm identity; old metadata remains explicitly unverified.
ALTER TABLE native_rag_index_version ADD COLUMN parser_revision VARCHAR(120) NOT NULL DEFAULT 'legacy-unverified';
