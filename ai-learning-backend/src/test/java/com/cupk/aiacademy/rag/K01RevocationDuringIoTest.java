package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service, isolated H2 and actual private files; model and identity changes are deterministic test callbacks. */
class K01RevocationDuringIoTest {
    private static final String PRIVATE_MARKER="K01_PRIVATE_REVOCATION_SOURCE";
    private static final String SOURCE=PRIVATE_MARKER+": Gradient descent minimizes training loss.";
    @TempDir Path temporary;
    private final ObjectMapper mapper=new ObjectMapper();
    private final Map<Integer,String> roles=new HashMap<>();
    private JdbcTemplate jdbc;
    private NativeRagService service;
    private RagEmbeddingClient embedding;
    private RagFileStore files;
    private String base;
    private String document;

    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        roles.put(1,"teacher");
        var permissions=mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(call->{
            Integer id=call.getArgument(0);
            if(!roles.containsKey(id))throw new ForbiddenException("Account disabled during I/O");
            var actor=new AuthUtils.LoginUser();actor.setUserId(id);actor.setRoleCode(roles.get(id));return actor;
        });
        embedding=mock(RagEmbeddingClient.class);
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.fingerprint()).thenReturn("revocation-test-fingerprint");
        when(embedding.modelName()).thenReturn("revocation-test-model");
        when(embedding.embed(anyList())).thenAnswer(call->((List<?>)call.getArgument(0)).stream().map(ignored->new double[]{1,0}).toList());
        files=spy(new RagFileStore(temporary.resolve("private").toString()));
        service=new NativeRagService(jdbc,new RagDocumentParser(),embedding,mapper,
                new RagAuthorizationService(permissions,jdbc),files,new RagSchemaMigrator(jdbc),new RagIndexVersionRepository(jdbc));
        service.initialize();
        var context=new AuthUtils.LoginUser();context.setUserId(1);context.setRoleCode("teacher");UserContext.set(context);
        base=data(service.createKnowledgeBase("Private revocation test","")).get("id").toString();
        document=data(service.uploadFile(base,SOURCE.getBytes(StandardCharsets.UTF_8),"synthetic-private.txt")).get("id").toString();
        clearInvocations(embedding,files);
    }

    @AfterEach void cleanup() { UserContext.clear();jdbc.execute("DROP ALL OBJECTS"); }

    @ParameterizedTest @ValueSource(strings={"student","disabled"})
    void searchMustReauthorizeAfterQueryEmbeddingBeforeReturningPrivateHits(String change) throws Exception {
        when(embedding.embed(anyList())).thenAnswer(call->{
            if("disabled".equals(change))roles.remove(1);else roles.put(1,change);
            return List.of(new double[]{1,0});
        });

        Result result=service.searchForOwner(List.of(base),"gradient descent",1,3);

        verify(embedding).embed(List.of("gradient descent"));
        assertAll(
                ()->assertEquals("403",result.getCode(),"The account or teaching role was revoked while the model call was running"),
                ()->assertFalse(mapper.writeValueAsString(result).contains(PRIVATE_MARKER),"A denied response cannot contain previously loaded private source text"));
    }

    @Test void privateOriginalMustReauthorizeAfterVerificationBeforeReturningItsPath() {
        var actualVerification=new AtomicBoolean();
        doAnswer(call->{
            Object path=call.callRealMethod();
            actualVerification.set(true);
            roles.put(1,"student");
            return path;
        }).when(files).verified(anyString(),anyLong(),anyString());

        var denied=assertThrows(RagAccessException.class,()->service.originalFile(document));

        assertTrue(actualVerification.get(),"The real size and SHA verification must run before revocation");
        assertEquals("403",denied.code());
    }

    @SuppressWarnings("unchecked") private Map<String,Object> data(Result result) {
        assertEquals("200",result.getCode(),result.getMsg());return (Map<String,Object>)result.getData();
    }
}
