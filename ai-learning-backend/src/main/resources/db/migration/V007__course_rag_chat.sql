ALTER TABLE t_native_rag_session ADD COLUMN course_id INT NULL;
ALTER TABLE t_native_rag_session ADD COLUMN lesson_id INT NULL;
ALTER TABLE t_native_rag_session ADD COLUMN scope_json MEDIUMTEXT NULL;
ALTER TABLE t_native_rag_message ADD COLUMN selected_text TEXT NULL;
