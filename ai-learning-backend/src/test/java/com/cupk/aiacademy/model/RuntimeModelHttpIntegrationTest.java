package com.cupk.aiacademy.model;

import com.cupk.aiacademy.common.JwtUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Real servlet/JWT/database authorization, with every external model deliberately unconfigured. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:k00-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "runtime.models.generation.base-url=",
        "runtime.models.generation.api-key=",
        "runtime.models.generation.model=",
        "runtime.models.planning.base-url=",
        "runtime.models.planning.api-key=",
        "runtime.models.planning.model=",
        "runtime.models.embedding.enabled=false",
        "runtime.models.embedding.base-url=",
        "runtime.models.embedding.api-key=",
        "runtime.models.embedding.model=",
        "runtime.models.ocr.base-url=",
        "runtime.models.ocr.api-key=",
        "runtime.models.ocr.model=",
        "weknora.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuntimeModelHttpIntegrationTest {
    private static final java.nio.file.Path PRIVATE_FILES=privateDirectory();
    private static java.nio.file.Path privateDirectory(){try{return java.nio.file.Files.createTempDirectory("k01-http-");}catch(Exception e){throw new IllegalStateException("Test directory unavailable");}}
    @DynamicPropertySource static void privateFiles(DynamicPropertyRegistry registry){registry.add("rag.storage-path",PRIVATE_FILES::toString);}
    private static final int ADMIN_ID = 9001;
    private static final int TEACHER_ID = 9002;
    private static final int STUDENT_ID = 9003;
    private static final String STATUS_PATH = "/api/admin/models/status";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtils jwt;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext applicationContext;
    @Autowired NativeRagService knowledge;
    @Autowired com.cupk.aiacademy.rag.ingestion.RagIngestionService ingestion;

    // A real service spy only observes routing; it does not replace the implementation or model beans.
    @SpyBean RuntimeModelCapabilityService capabilityService;

    @BeforeEach
    void seedRealUsersAndRoles() {
        jdbc.update("DELETE FROM t_user_role WHERE user_id IN (?, ?, ?)", ADMIN_ID, TEACHER_ID, STUDENT_ID);
        jdbc.update("DELETE FROM t_user WHERE id IN (?, ?, ?)", ADMIN_ID, TEACHER_ID, STUDENT_ID);
        jdbc.update("DELETE FROM t_role WHERE id IN (?, ?, ?)", ADMIN_ID, TEACHER_ID, STUDENT_ID);
        seedUser(ADMIN_ID, "admin");
        seedUser(TEACHER_ID, "teacher");
        seedUser(STUDENT_ID, "student");
        clearInvocations(capabilityService);
    }

    private void seedUser(int id, String role) {
        String username = "k00-http-" + role;
        jdbc.update("INSERT INTO t_role(id, role_name, role_code, create_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                id, "K00 " + role, role);
        jdbc.update("INSERT INTO t_user(id, username, password, nickname, status, create_time, update_time) "
                        + "VALUES (?, ?, 'test-only-unused-password', ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, username, "K00 " + role);
        jdbc.update("INSERT INTO t_user_role(id, user_id, role_id) VALUES (?, ?, ?)", id, id, id);
    }

    @AfterEach void removeSyntheticMaterials() {
        for(var row:jdbc.queryForList("SELECT id,owner_id FROM native_rag_base WHERE owner_id IN (?,?)",ADMIN_ID,TEACHER_ID)) {
            var user=new AuthUtils.LoginUser();user.setUserId(((Number)row.get("owner_id")).intValue());
            user.setRoleCode(user.getUserId()==ADMIN_ID?"admin":"teacher");UserContext.set(user);
            try{knowledge.deleteKnowledgeBase(row.get("id").toString());}finally{UserContext.clear();}
        }
    }

    @Test void studentPersonalLibraryWriteIsDeniedByTheRealBackend() throws Exception {
        var response=requestWithBody(HttpMethod.POST,"/api/rag/knowledge-bases",STUDENT_ID,
                "{\"name\":\"student personal\",\"description\":\"\"}");
        assertEquals("403",mapper.readTree(response.getBody()).path("code").asText());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_base WHERE owner_id=?",Integer.class,STUDENT_ID));
    }

    @Test void asynchronousUploadExposesAJobAndProtectsItsStatus() throws Exception {
        var user=new AuthUtils.LoginUser();user.setUserId(TEACHER_ID);user.setRoleCode("teacher");UserContext.set(user);
        String base;
        try{base=((java.util.Map<?,?>)knowledge.createKnowledgeBase("Queued HTTP sample","").getData()).get("id").toString();}
        finally{UserContext.clear();}
        var headers=new HttpHeaders();headers.setBearerAuth(jwt.createToken(TEACHER_ID,"k02-http","teacher"));
        var body=new org.springframework.util.LinkedMultiValueMap<String,Object>();
        body.add("file",new org.springframework.core.io.ByteArrayResource("Queued synthetic course text.".getBytes(java.nio.charset.StandardCharsets.UTF_8)){
            @Override public String getFilename(){return "notes.txt";}
        });
        var uploaded=http.postForEntity("/api/rag/knowledge-bases/"+base+"/documents",new HttpEntity<>(body,headers),String.class);
        var data=mapper.readTree(uploaded.getBody()).path("data");assertTrue(data.path("jobId").isTextual());
        assertEquals("queued",data.path("parse_status").asText());
        String job=data.path("jobId").asText();
        assertEquals("QUEUED",mapper.readTree(request(HttpMethod.GET,"/api/rag/jobs/"+job,TEACHER_ID).getBody()).path("data").path("stage").asText());
        assertEquals("403",mapper.readTree(request(HttpMethod.GET,"/api/rag/jobs/"+job,STUDENT_ID).getBody()).path("code").asText());
    }

    @Test void explicitVersionGrantAndIndependentDownloadPermissionWorkThroughRealHttp() throws Exception {
        jdbc.update("MERGE INTO t_course(id,teacher_id,title,status) KEY(id) VALUES(9010,?,'Synthetic course',1)",TEACHER_ID);
        jdbc.update("MERGE INTO t_teaching_class(id,course_id,teacher_id,status) KEY(id) VALUES(9010,9010,?,1)",TEACHER_ID);
        jdbc.update("MERGE INTO t_class_student(id,class_id,student_id,status) KEY(id) VALUES(9010,9010,?,1)",STUDENT_ID);
        String document=privateDocument();
        String route="/api/rag/courses/9010/documents/"+document+"/versions/1";
        var denied=request(HttpMethod.GET,route,STUDENT_ID);
        assertEquals("403",mapper.readTree(denied.getBody()).path("code").asText());
        var granted=requestWithBody(HttpMethod.POST,"/api/rag/documents/"+document+"/grants",TEACHER_ID,
                "{\"courseId\":9010,\"documentVersion\":1,\"readExcerpt\":true,\"downloadAllowed\":false}");
        assertEquals("200",mapper.readTree(granted.getBody()).path("code").asText());
        var content=mapper.readTree(request(HttpMethod.GET,route,STUDENT_ID).getBody());
        assertEquals("200",content.path("code").asText());assertTrue(content.path("data").path("content").asText().contains("Synthetic private source"));
        assertEquals(HttpStatus.FORBIDDEN,request(HttpMethod.GET,route+"/download",STUDENT_ID).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,request(HttpMethod.GET,"/api/rag/documents/"+document+"/download",ADMIN_ID).getStatusCode());
        String fileKey=jdbc.queryForObject("SELECT file_key FROM native_rag_document WHERE id=?",String.class,document);
        var staticResponse=http.getForEntity("/uploads/"+fileKey,String.class);
        assertFalse(staticResponse.getStatusCode().is2xxSuccessful());
        var downloadable=requestWithBody(HttpMethod.POST,"/api/rag/documents/"+document+"/grants",TEACHER_ID,
                "{\"courseId\":9010,\"documentVersion\":1,\"readExcerpt\":true,\"downloadAllowed\":true}");
        assertEquals("200",mapper.readTree(downloadable.getBody()).path("code").asText());
        var original=request(HttpMethod.GET,route+"/download",STUDENT_ID);
        assertEquals(HttpStatus.OK,original.getStatusCode());assertTrue(original.getBody().contains("Synthetic private source"));
        assertEquals("private, no-store",original.getHeaders().getCacheControl());
        jdbc.update("UPDATE t_class_student SET status=0 WHERE id=9010");
        var revoked=mapper.readTree(request(HttpMethod.GET,route,STUDENT_ID).getBody());assertEquals("403",revoked.path("code").asText());
        assertFalse(revoked.toString().contains("Synthetic private source"));
    }

    private String privateDocument() {
        var user=new AuthUtils.LoginUser();user.setUserId(TEACHER_ID);user.setRoleCode("teacher");UserContext.set(user);
        try {
            var base=knowledge.createKnowledgeBase("Synthetic HTTP materials","");assertEquals("200",base.getCode());
            String id=((java.util.Map<?,?>)base.getData()).get("id").toString();
            var doc=knowledge.uploadFile(id,"Synthetic private source for course authorization.".getBytes(java.nio.charset.StandardCharsets.UTF_8),"synthetic.txt");
            assertEquals("200",doc.getCode());ingestion.runNext("http-fixture");return ((java.util.Map<?,?>)doc.getData()).get("id").toString();
        } finally {UserContext.clear();}
    }

    private ResponseEntity<String> requestWithBody(HttpMethod method,String path,int userId,String body) {
        String role=userId==STUDENT_ID?"student":"teacher";
        var headers=new HttpHeaders();headers.setBearerAuth(jwt.createToken(userId,"k01-http",role));headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
    }

    @Test
    void anonymousStatusRequestIsRejectedByRealAuthenticationInterceptor() {
        var response = http.getForEntity(STATUS_PATH, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(capabilityService, never()).status();
    }

    @Test
    void teacherAndStudentCannotReadStatusOrTriggerProbe() {
        for (int id : List.of(TEACHER_ID, STUDENT_ID)) {
            assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, STATUS_PATH, id).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                    request(HttpMethod.POST, "/api/admin/models/generation/probe", id).getStatusCode());
        }
        verify(capabilityService, never()).status();
        verify(capabilityService, never()).probe(anyString());
    }

    @Test
    void administratorReceivesFourReadOnlyStatusesWithoutEndpointOrCredentialFields() throws Exception {
        var response = request(HttpMethod.GET, STATUS_PATH, ADMIN_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals("200", body.path("code").asText());
        JsonNode statuses = body.path("data");
        assertTrue(statuses.isArray());
        assertEquals(4, statuses.size());
        Set<String> roles = new HashSet<>();
        for (JsonNode status : statuses) {
            roles.add(status.path("role").asText());
            assertEquals(status.path("role").asText().equals("embedding")
                    ? "MODEL_DISABLED" : "MODEL_NOT_CONFIGURED", status.path("configuration").asText());
            assertFalse(status.has("baseUrl"));
            assertFalse(status.has("base-url"));
            assertFalse(status.has("apiKey"));
            assertFalse(status.has("api-key"));
        }
        assertEquals(Set.of("generation", "planning", "embedding", "ocr"), roles);
        assertFalse(response.getBody().contains("http://"));
        assertFalse(response.getBody().contains("https://"));
        verify(capabilityService, never()).probe(anyString());
    }

    @Test
    void administratorGenerationProbeIsSkippedWhenModelIsUnconfigured() throws Exception {
        assertSkippedProbe("generation", "MODEL_NOT_CONFIGURED");
    }

    @Test
    void administratorOcrProbeIsSkippedWithoutSelectingAModel() throws Exception {
        assertSkippedProbe("ocr", "MODEL_NOT_CONFIGURED");
    }

    @Test
    void administratorUnknownRoleProbeIsBadRequest() {
        var response = request(HttpMethod.POST, "/api/admin/models/unknown/probe", ADMIN_ID);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(capabilityService, never()).probe(anyString());
    }

    @Test
    void getCannotExecuteThePostOnlyProbe() {
        var response = request(HttpMethod.GET, "/api/admin/models/generation/probe", ADMIN_ID);
        assertFalse(response.getStatusCode().is2xxSuccessful());
        verify(capabilityService, never()).probe(anyString());
    }

    @Test
    void legacyHealthCheckRejectsNonAdministrators() {
        for (int id : List.of(TEACHER_ID, STUDENT_ID)) {
            assertEquals(HttpStatus.FORBIDDEN,
                    request(HttpMethod.GET, "/api/agent/model/health", id).getStatusCode());
        }
    }

    @Test
    void applicationHasOnlyTwoExplicitGuardedChatModelsAndNoImplicitEmbeddingModel() {
        var chatModels = applicationContext.getBeansOfType(OpenAiChatModel.class);
        assertEquals(Set.of("generationChatModel", "planningChatModel"), chatModels.keySet());
        chatModels.values().forEach(model -> assertInstanceOf(GuardedChatModel.class, model));
        assertTrue(applicationContext.getBeansOfType(OpenAiEmbeddingModel.class).isEmpty());
    }

    private void assertSkippedProbe(String role, String expectedDetail) throws Exception {
        var response = request(HttpMethod.POST, "/api/admin/models/" + role + "/probe", ADMIN_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode report = mapper.readTree(response.getBody()).path("data");
        assertEquals(role, report.path("role").asText());
        assertEquals("SKIPPED", report.path("status").asText());
        assertEquals(1, report.path("checks").size());
        JsonNode check = report.path("checks").get(0);
        assertEquals("SKIPPED", check.path("status").asText());
        assertEquals(expectedDetail, check.path("detail").asText());
        assertEquals(0L, check.path("latencyMs").asLong());
    }

    private ResponseEntity<String> request(HttpMethod method, String path, int userId) {
        String role = userId == ADMIN_ID ? "admin" : userId == TEACHER_ID ? "teacher" : "student";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.createToken(userId, "k00-http-" + role, role));
        return http.exchange(path, method, new HttpEntity<Void>(headers), String.class);
    }
}
