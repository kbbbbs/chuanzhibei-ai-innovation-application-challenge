package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class K01AuthorizationBoundaryTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    NativeRagService service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        service=new NativeRagService(jdbc,new RagDocumentParser(),new RagEmbeddingClient(false,"","","",new ObjectMapper()),new ObjectMapper(),temp.toString());
        service.initialize();login(1,"teacher");
    }
    @AfterEach void cleanup(){UserContext.clear();jdbc.execute("DROP ALL OBJECTS");}
    void login(int id,String role){var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(role);UserContext.set(user);}
    @SuppressWarnings("unchecked") Map<String,Object> data(Result value){assertEquals("200",value.getCode(),value.getMsg());return (Map<String,Object>)value.getData();}
    String base(){return data(service.createKnowledgeBase("Private teacher notes","")).get("id").toString();}
    String upload(String base){return data(service.uploadFile(base,"Gradient descent minimizes loss.".getBytes(StandardCharsets.UTF_8),"notes.txt")).get("id").toString();}

    @Test void studentCannotCreateOrMutateEvenALegacyOwnedLibrary() {
        String base=base();
        String document=upload(base);
        jdbc.update("UPDATE native_rag_base SET owner_id=2 WHERE id=?",base);
        login(2,"student");
        assertEquals("403",service.createKnowledgeBase("student personal","").getCode());
        assertEquals("403",service.updateKnowledgeBase(base,"renamed",null,true).getCode());
        assertEquals("403",service.uploadFile(base,"new".getBytes(StandardCharsets.UTF_8),"new.txt").getCode());
        assertEquals("403",service.reindexDocument(document).getCode());
        assertEquals("403",service.deleteDocument(document).getCode());
        assertEquals("403",service.deleteKnowledgeBase(base).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_base",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document",Integer.class));
    }

    @Test void legacySharedFlagDoesNotAuthorizeOtherTeachersStudentsOrAdministrators() {
        String base=base();String document=upload(base);
        String chunk=jdbc.queryForObject("SELECT id FROM native_rag_chunk WHERE document_id=?",String.class,document);
        assertEquals("200",service.updateKnowledgeBase(base,null,null,true).getCode());
        for(String role:List.of("teacher","student","admin")) {
            login(2,role);
            assertEquals("403",service.getKnowledgeBase(base).getCode(),role);
            assertEquals("403",service.getDocument(document).getCode(),role);
            assertEquals("403",service.listKnowledge(base,10,0).getCode(),role);
            assertEquals("403",service.chunk(chunk).getCode(),role);
            assertEquals("403",service.chunks(document).getCode(),role);
            assertEquals("403",service.search(new RagSearchRequest(List.of(base),"gradient",3)).getCode(),role);
            assertEquals("403",assertThrows(RagAccessException.class,()->service.originalFile(document)).code(),role);
            assertTrue(((List<?>)service.listKnowledgeBases().getData()).isEmpty(),role);
        }
    }

    @Test void anExplicitActorIdCannotImpersonateTheOwnerInAnAuthenticatedRequest() {
        String base=base();String document=upload(base);login(2,"teacher");
        assertEquals("403",service.contentForOwner(document,1).getCode());
        assertEquals("403",service.listKnowledgeForOwner(base,1,10,0).getCode());
        assertEquals("403",service.searchForOwner(List.of(base),"gradient",1,3).getCode());
        assertEquals("403",assertThrows(RagAccessException.class,()->service.requireReadableBaseIdsForOwner(List.of(base),1)).code());
    }

    @Test void missingOrUnknownRoleCannotAcquireWritePrivileges() {
        login(2,"");assertEquals("403",service.createKnowledgeBase("unknown","").getCode());
        login(2,"other");assertEquals("403",service.createKnowledgeBase("unknown","").getCode());
        UserContext.clear();assertEquals("401",service.createKnowledgeBase("anonymous","").getCode());
    }

    @Test void newDocumentsExposeAnImmutableDocumentVersionAndAnActiveIndex() {
        String base=base();String document=upload(base);
        var value=data(service.getDocument(document));
        assertEquals(1,((Number)value.get("document_version")).intValue());
        assertEquals(1,((Number)value.get("parse_revision")).intValue());
        assertNotNull(value.get("active_index_version"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=?",Integer.class,document));
    }
}
