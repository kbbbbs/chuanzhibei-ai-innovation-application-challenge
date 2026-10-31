package com.cupk.aiacademy.rag.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Data-only version switching. Callers must authorize the actor before accessing this repository. */
@org.springframework.stereotype.Repository
public class RagIndexVersionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public RagIndexVersionRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public String createStaging(String documentId,int documentVersion,int parseRevision,String chunkRevision,
                                String embeddingFingerprint,String embeddingModel,int dimensions) {
        return createStaging(documentId,documentVersion,parseRevision,chunkRevision,embeddingFingerprint,embeddingModel,dimensions,"legacy-unverified");
    }
    public String createStaging(String documentId,int documentVersion,int parseRevision,String chunkRevision,
                                String embeddingFingerprint,String embeddingModel,int dimensions,String parserRevision) {
        if(documentVersion<1 || parseRevision<1 || dimensions<0 || dimensions>4096 || chunkRevision==null || chunkRevision.isBlank())
            throw new IllegalArgumentException("Invalid index metadata");
        int documents=jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE id=? AND document_version=? AND parse_revision=?",Integer.class,documentId,documentVersion,parseRevision);
        if(documents!=1) throw new IllegalStateException("Document revision changed before indexing");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO native_rag_index_version(id,document_id,document_version,parse_revision,chunk_revision,embedding_fingerprint,embedding_model,dimensions,normalized,state) VALUES(?,?,?,?,?,?,?,?,?,'BUILDING')",
                id,documentId,documentVersion,parseRevision,chunkRevision,embeddingFingerprint,embeddingModel,dimensions,dimensions>0);
        jdbc.update("UPDATE native_rag_index_version SET parser_revision=? WHERE id=?",parserRevision==null?"legacy-unverified":parserRevision,id);
        return id;
    }

    /** REQUIRED propagation joins upload/replace transactions, including the per-document-version pointer. */
    public void activate(String documentId,String versionId,String expectedActiveVersion,String readyStatus) {
        if(!List.of("KEYWORD_READY","HYBRID_READY").contains(readyStatus)) throw new IllegalArgumentException("Index must be ready before activation");
        transaction.executeWithoutResult(status->{
            var documents=jdbc.queryForList("SELECT * FROM native_rag_document WHERE id=? FOR UPDATE",documentId);
            var indexes=jdbc.queryForList("SELECT * FROM native_rag_index_version WHERE id=? AND document_id=?",versionId,documentId);
            if(documents.size()!=1 || indexes.size()!=1) throw new IllegalStateException("Index or document is unavailable");
            var document=documents.getFirst();var index=indexes.getFirst();
            if(!"BUILDING".equals(index.get("state")) || !Objects.equals(document.get("active_index_version"),expectedActiveVersion)
                    || ((Number)document.get("document_version")).intValue()!=((Number)index.get("document_version")).intValue()
                    || ((Number)document.get("parse_revision")).intValue()!=((Number)index.get("parse_revision")).intValue())
                throw new IllegalStateException("Index activation lost its document revision");
            int chunks=jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=? AND index_version=? AND document_version=? AND parse_revision=?",Integer.class,
                    documentId,versionId,index.get("document_version"),index.get("parse_revision"));
            if(chunks==0 || chunks!=((Number)document.get("chunk_count")).intValue()) throw new IllegalStateException("Incomplete index cannot become active");
            if("HYBRID_READY".equals(readyStatus) && (((Number)index.get("dimensions")).intValue()<1 ||
                    jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=? AND index_version=? AND vector_json IS NULL",Integer.class,documentId,versionId)>0))
                throw new IllegalStateException("Hybrid index has incomplete vectors");
            int changed=expectedActiveVersion==null
                    ?jdbc.update("UPDATE native_rag_document SET active_index_version=? WHERE id=? AND active_index_version IS NULL",versionId,documentId)
                    :jdbc.update("UPDATE native_rag_document SET active_index_version=? WHERE id=? AND active_index_version=?",versionId,documentId,expectedActiveVersion);
            if(changed!=1) throw new IllegalStateException("Index activation lost its compare and swap");
            jdbc.update("UPDATE native_rag_index_version SET state=?,completed_at=CURRENT_TIMESTAMP WHERE id=? AND state='BUILDING'",readyStatus,versionId);
            // Old versions remain addressable by an explicit grant after the latest document advances.
            jdbc.update("UPDATE native_rag_document_version SET active_index_version=? WHERE document_id=? AND document_version=?",versionId,documentId,index.get("document_version"));
        });
    }

    public void markFailed(String versionId) {
        jdbc.update("UPDATE native_rag_index_version SET state='FAILED',completed_at=CURRENT_TIMESTAMP WHERE id=? AND state='BUILDING'",versionId);
    }
}
