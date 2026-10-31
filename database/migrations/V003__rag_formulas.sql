-- K02_003. Existing parse revisions remain unchanged; reparse to populate formula metadata.
ALTER TABLE native_rag_page ADD COLUMN formula_json LONGTEXT;
