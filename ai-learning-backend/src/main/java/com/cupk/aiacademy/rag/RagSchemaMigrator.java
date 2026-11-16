package com.cupk.aiacademy.rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Resumable additive K01 migration; no course grant is inferred from a legacy shared flag. */
@org.springframework.stereotype.Component
public class RagSchemaMigrator {
    private static final Pattern ADD_COLUMN=Pattern.compile("(?is)^ALTER TABLE ([a-z_]+) ADD COLUMN ([a-z_]+) .*\\z");
    private final JdbcTemplate jdbc;
    public RagSchemaMigrator(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void migrate() {
        applyResource("V001__rag_foundation.sql");
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status->{
            // Later K02 uploads may intentionally have no active index while their jobs are queued.
            // Only the first application may classify pre-K01 rows as legacy data.
            if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K01_001'",Integer.class)>0) return;
            for(var document:jdbc.queryForList("SELECT d.*,b.owner_id FROM native_rag_document d JOIN native_rag_base b ON b.id=d.kb_id"))
                backfill(document);
            jdbc.update("INSERT INTO native_rag_schema_migration(version) VALUES('K01_001')");
        });
        applyResource("V002__rag_ingestion.sql");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K02_002'",Integer.class)==0)
            jdbc.update("INSERT INTO native_rag_schema_migration(version) VALUES('K02_002')");
        applyResource("V003__rag_formulas.sql");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K02_003'",Integer.class)==0)
            jdbc.update("INSERT INTO native_rag_schema_migration(version) VALUES('K02_003')");
        applyResource("V004__rag_visuals.sql");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K02_004'",Integer.class)==0)
            jdbc.update("INSERT INTO native_rag_schema_migration(version) VALUES('K02_004')");
        applyResource("V005__rag_parser_revision.sql");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K03_005'",Integer.class)==0)
            jdbc.update("INSERT INTO native_rag_schema_migration(version) VALUES('K03_005')");
        applyResource("V006__chapter_graph.sql");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_schema_migration WHERE version='K09_006'",Integer.class)==0)
            jdbc.update("INSERT INTO native_rag_schema_migration(version) VALUES('K09_006')");
    }

    private void applyResource(String name) {
        String script;
        try(var source=new ClassPathResource("db/migration/"+name).getInputStream()) {
            script=new String(source.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$","");
        } catch(Exception ignored) { throw new IllegalStateException("Native RAG migration resource unavailable"); }
        for(String sql:script.split(";")) {
            String statement=sql.strip();
            if(statement.isEmpty()) continue;
            var addition=ADD_COLUMN.matcher(statement);
            if(addition.matches() && hasColumn(addition.group(1),addition.group(2))) continue;
            jdbc.execute(statement);
        }
    }

    private boolean hasColumn(String table,String column) {
        try(var connection=jdbc.getDataSource().getConnection()) {
            var metadata=connection.getMetaData();
            String escape=metadata.getSearchStringEscape();
            String tablePattern=metadata.storesUpperCaseIdentifiers()?table.toUpperCase(java.util.Locale.ROOT):table;
            String columnPattern=metadata.storesUpperCaseIdentifiers()?column.toUpperCase(java.util.Locale.ROOT):column;
            tablePattern=tablePattern.replace("_",escape+"_");columnPattern=columnPattern.replace("_",escape+"_");
            try(var rows=metadata.getColumns(connection.getCatalog(),null,tablePattern,columnPattern)) {
                while(rows.next()) if(table.equalsIgnoreCase(rows.getString("TABLE_NAME")) && column.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) return true;
            }
            return false;
        } catch(SQLException ignored) { throw new IllegalStateException("Native RAG migration metadata unavailable"); }
    }

    private void backfill(Map<String,Object> document) {
        String id=document.get("id").toString();
        int version=((Number)document.get("document_version")).intValue();
        int parseRevision=((Number)document.get("parse_revision")).intValue();
        String active=document.get("active_index_version")==null?null:document.get("active_index_version").toString();
        if(active==null) {
            // A stable identifier makes an interrupted/backfilled legacy import idempotent.
            active=id;
            if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version WHERE id=?",Integer.class,active)==0) {
                int count=jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,id);
                jdbc.update("INSERT INTO native_rag_index_version(id,document_id,document_version,parse_revision,chunk_revision,embedding_fingerprint,embedding_model,dimensions,normalized,state,completed_at) VALUES(?,?,?,?,?,'legacy-unverified','',0,FALSE,?,CURRENT_TIMESTAMP)",
                        active,id,version,parseRevision,"legacy-unknown",count>0?"KEYWORD_READY":"DEGRADED");
            }
            jdbc.update("UPDATE native_rag_chunk SET index_version=?,document_version=?,parse_revision=? WHERE document_id=? AND index_version IS NULL",active,version,parseRevision,id);
            jdbc.update("UPDATE native_rag_document SET active_index_version=? WHERE id=? AND active_index_version IS NULL",active,id);
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=? AND document_version=?",Integer.class,id,version)==0)
            jdbc.update("INSERT INTO native_rag_document_version(document_id,document_version,file_key,sha256,file_name,mime_type,file_size,actor_id,active_index_version,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    id,version,document.get("file_key"),document.get("sha256"),document.get("file_name"),document.get("mime_type"),document.get("file_size"),document.get("owner_id"),active,document.get("created_at"));
        else jdbc.update("UPDATE native_rag_document_version SET active_index_version=? WHERE document_id=? AND document_version=? AND active_index_version IS NULL",active,id,version);
    }
}
