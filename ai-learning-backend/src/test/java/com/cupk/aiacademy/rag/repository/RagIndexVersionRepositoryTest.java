package com.cupk.aiacademy.rag.repository;

import com.cupk.aiacademy.rag.RagSchemaMigrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagIndexVersionRepositoryTest {
    JdbcTemplate db; RagIndexVersionRepository repository;
    String base=UUID.randomUUID().toString(), document=UUID.randomUUID().toString();
    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        new RagSchemaMigrator(db).migrate();
        db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES(?,1,'test','')",base);
        db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status) VALUES(?,?,'test','a.txt',?,'sha','text/plain',12,0,1,'not_configured')",document,base,UUID.randomUUID().toString());
        db.update("INSERT INTO native_rag_document_version(document_id,document_version,file_key,sha256,file_name,mime_type,file_size,actor_id) SELECT id,1,file_key,sha256,file_name,mime_type,file_size,1 FROM native_rag_document WHERE id=?",document);
        repository=new RagIndexVersionRepository(db);
    }
    @Test void buildingVersionIsNotVisibleAndReadyVersionIsActivatedAtomically() {
        String first=stage();
        assertNull(active());assertEquals("BUILDING",state(first));
        chunk(first,"old source");
        repository.activate(document,first,null,"KEYWORD_READY");
        assertEquals(first,active());assertEquals("KEYWORD_READY",state(first));
        String next=stage();chunk(next,"new source");
        assertEquals(first,active());
        repository.activate(document,next,first,"HYBRID_READY");
        assertEquals(next,active());
        assertEquals(next,db.queryForObject("SELECT active_index_version FROM native_rag_document_version WHERE document_id=? AND document_version=1",String.class,document));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE document_id=?",Integer.class,document));
        assertEquals("new source",db.queryForObject("SELECT c.content FROM native_rag_chunk c JOIN native_rag_document d ON d.active_index_version=c.index_version WHERE d.id=?",String.class,document));
    }
    @Test void failedOrEmptyStagingVersionCannotReplaceAUsableIndex() {
        String first=stage();chunk(first,"old");repository.activate(document,first,null,"KEYWORD_READY");
        String empty=stage();assertThrows(IllegalStateException.class,()->repository.activate(document,empty,first,"KEYWORD_READY"));
        assertEquals(first,active());assertEquals("BUILDING",state(empty));
        String failed=stage();chunk(failed,"partial");repository.markFailed(failed);
        assertThrows(IllegalStateException.class,()->repository.activate(document,failed,first,"KEYWORD_READY"));
        assertEquals(first,active());assertEquals("FAILED",state(failed));
    }
    @Test void staleWriterLosesCompareAndSwapWithoutReplacingTheWinner() {
        String first=stage();chunk(first,"old");repository.activate(document,first,null,"KEYWORD_READY");
        String winner=stage();chunk(winner,"winner");String loser=stage();chunk(loser,"loser");
        repository.activate(document,winner,first,"KEYWORD_READY");
        assertThrows(IllegalStateException.class,()->repository.activate(document,loser,first,"KEYWORD_READY"));
        assertEquals(winner,active());assertEquals("BUILDING",state(loser));
    }
    @Test void mismatchedDocumentVersionOrForeignIndexCannotBeActivated() {
        String staged=stage();chunk(staged,"content");
        db.update("UPDATE native_rag_document SET document_version=2 WHERE id=?",document);
        assertThrows(IllegalStateException.class,()->repository.activate(document,staged,null,"KEYWORD_READY"));
        assertNull(active());
        assertThrows(IllegalStateException.class,()->repository.activate(UUID.randomUUID().toString(),staged,null,"KEYWORD_READY"));
    }
    private String stage(){return repository.createStaging(document,1,1,"chunk-v1","test-fingerprint","model-test",2);}
    private String active(){return db.queryForObject("SELECT active_index_version FROM native_rag_document WHERE id=?",String.class,document);}
    private String state(String id){return db.queryForObject("SELECT state FROM native_rag_index_version WHERE id=?",String.class,id);}
    private void chunk(String version,String content){db.update("INSERT INTO native_rag_chunk(id,document_id,kb_id,ordinal_number,content,index_version,document_version,parse_revision,vector_json,vector_model) VALUES(?,?,?,1,?,?,1,1,'[1,0]','test-fingerprint')",UUID.randomUUID().toString(),document,base,content,version);}
}
