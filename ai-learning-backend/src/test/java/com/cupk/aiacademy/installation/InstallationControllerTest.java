package com.cupk.aiacademy.installation;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.installation.InstallationLifecycle;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InstallationControllerTest {
    @TempDir Path directory;
    private MockMvc mvc;
    private InstallationConfigStore store;
    private InstallationStateStore state;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void start() {
        state = new InstallationStateStore(directory, Map.of());
        store = new InstallationConfigStore(directory, Map.of());
        var sessions = new InstallationSessions(directory);
        var context = new StaticApplicationContext();
        var resolver =
                new DatabaseDraftResolver(
                        Map.of(
                                "AI_MANAGED_DB_HOST",
                                "127.0.0.1",
                                "AI_MANAGED_DB_PORT",
                                "1",
                                "AI_MANAGED_DB_USERNAME",
                                "isolated_test",
                                "AI_MANAGED_DB_PASSWORD",
                                "ephemeral-fixture"),
                        state);
        var controller =
                new InstallationController(
                        state,
                        store,
                        sessions,
                        new DatabaseInstaller(),
                        resolver,
                        new InstallationLifecycle(directory, Map.of()),
                        new DraftModelProbeService(store),
                        context.getBeanProvider(AuthUtils.class),
                        context);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new InstallationErrorHandler())
                        .build();
    }

    @Test
    void publicStatusContainsOnlyInstallationState() throws Exception {
        var response =
                mvc.perform(get("/api/setup/status"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse();
        var data = json.readTree(response.getContentAsString()).get("data");
        assertEquals(3, data.size());
        assertEquals("UNINITIALIZED", data.get("state").asText());
        assertFalse(data.has("roles"));
        assertFalse(data.has("database"));
        assertFalse(data.has("activeRevision"));
    }

    @Test
    void administratorEndpointsRejectAnonymousAccessWithRealHttpStatus() throws Exception {
        mvc.perform(get("/api/admin/settings/models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.data.errorCode").value("ADMIN_REQUIRED"));
        mvc.perform(put("/api/admin/settings/models").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // AuthInterceptor skips /api/admin/settings/**, so every handler there must authorize on its
    // own.
    @Test
    void everyAdministratorSettingsEndpointRejectsAnonymousAccess() throws Exception {
        int checked = 0;
        for (var method : InstallationController.class.getDeclaredMethods()) {
            var get =
                    method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            var put =
                    method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
            var post =
                    method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
            String[] paths =
                    get != null
                            ? get.value()
                            : put != null
                                    ? put.value()
                                    : post != null ? post.value() : new String[0];
            for (String path : paths) {
                if (!path.startsWith("/api/admin/settings/")) continue;
                String uri = path.replaceAll("\\{[^}]+}", "unknown");
                // A trusted Origin is forgeable outside browsers; it must not stand in for
                // authorization.
                var request =
                        (get != null ? get(uri) : put != null ? put(uri) : post(uri))
                                .header("Origin", "http://localhost:5173")
                                .header("Idempotency-Key", "anonymous-check")
                                .contentType("application/json")
                                .content("{}");
                int code = mvc.perform(request).andReturn().getResponse().getStatus();
                assertTrue(
                        code == 401 || code == 403,
                        path + " answered " + code + " to an anonymous request");
                checked++;
            }
        }
        assertTrue(checked >= 5, "expected all administrator settings endpoints to be discovered");
    }

    @Test
    void sessionExchangeRequiresTrustedOriginAndConsumesCodeOnce() throws Exception {
        String code = InstallationSessions.issueHostCode(directory, "INSTALL");
        String body = json.writeValueAsString(Map.of("code", code));
        mvc.perform(post("/api/setup/session").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        var response =
                mvc.perform(
                                post("/api/setup/session")
                                        .header("Origin", "http://localhost:5173")
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
        assertFalse(response.getContentAsString().contains(code));
        mvc.perform(
                        post("/api/setup/session")
                                .header("Origin", "http://localhost:5173")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unreachableDatabaseIsUnavailableAndNeverReportedAsAnEmptyDatabase() throws Exception {
        String code = InstallationSessions.issueHostCode(directory, "INSTALL");
        var exchange =
                mvc.perform(
                                post("/api/setup/session")
                                        .header("Origin", "http://localhost:5173")
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(Map.of("code", code))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        String cookie = exchange.getHeader("Set-Cookie");
        String token = cookie.substring("ai_installation=".length(), cookie.indexOf(';'));
        String csrf = json.readTree(exchange.getContentAsString()).at("/data/csrfToken").asText();
        mvc.perform(
                        post("/api/setup/database/check")
                                .cookie(new Cookie("ai_installation", token))
                                .header("Origin", "http://localhost:5173")
                                .header("X-CSRF-Token", csrf)
                                .contentType("application/json")
                                .content("{\"database\":{\"mode\":\"MANAGED\"}}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("503"))
                .andExpect(jsonPath("$.data.errorCode").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.classification").doesNotExist());
    }

    @Test
    void databaseCheckCannotMutateWithoutCsrf() throws Exception {
        String code = InstallationSessions.issueHostCode(directory, "INSTALL");
        var exchange =
                mvc.perform(
                                post("/api/setup/session")
                                        .header("Origin", "http://localhost:5173")
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(Map.of("code", code))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        String cookie = exchange.getHeader("Set-Cookie");
        String token = cookie.substring("ai_installation=".length(), cookie.indexOf(';'));
        mvc.perform(
                        post("/api/setup/database/check")
                                .cookie(new Cookie("ai_installation", token))
                                .header("Origin", "http://localhost:5173")
                                .contentType("application/json")
                                .content("{\"database\":{\"mode\":\"MANAGED\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("CSRF_REJECTED"));
    }

    @Test
    void failedRollbackCasDoesNotChangePendingAndSameKeyReplaysOneOperation() throws Exception {
        var roles = new LinkedHashMap<String, Map<String, Object>>();
        for (String role : java.util.List.of("generation", "planning", "embedding", "ocr")) {
            var draft = new LinkedHashMap<String, Object>();
            draft.put("enabled", false);
            draft.put("baseUrl", "");
            draft.put("model", "");
            draft.put("timeoutSeconds", 30);
            draft.put("inheritFrom", null);
            draft.put("secretAction", "KEEP");
            roles.put(role, draft);
        }
        String active = String.valueOf(store.saveModels("initial", roles).get("pendingRevision"));
        store.activate(active);
        String pending = String.valueOf(store.saveModels(active, roles).get("pendingRevision"));
        var failed = new LinkedHashMap<String, Object>();
        failed.put("operationId", "failed-operation");
        failed.put("installationId", state.installationId());
        failed.put("targetRevision", pending);
        failed.put("previousRevision", active);
        failed.put("activeRevision", active);
        failed.put("status", "RECOVERY_REQUIRED");
        Files.writeString(
                directory.resolve("operations.json"),
                json.writeValueAsString(
                        Map.of(
                                "currentOperationId",
                                "failed-operation",
                                "confirmedActiveRevision",
                                active,
                                "operations",
                                Map.of("failed-operation", failed))));
        String code = InstallationSessions.issueHostCode(directory, "RECOVERY", "failed-operation");
        var exchange =
                mvc.perform(
                                post("/api/setup/recovery/session")
                                        .header("Origin", "http://localhost:5173")
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(Map.of("code", code))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        String header = exchange.getHeader("Set-Cookie");
        Cookie cookie =
                new Cookie(
                        "ai_recovery",
                        header.substring("ai_recovery=".length(), header.indexOf(';')));
        String csrf = json.readTree(exchange.getContentAsString()).at("/data/csrfToken").asText();
        mvc.perform(
                        post("/api/setup/recovery/rollback")
                                .cookie(cookie)
                                .header("Origin", "http://localhost:5173")
                                .header("X-CSRF-Token", csrf)
                                .header("Idempotency-Key", "wrong-cas")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "operationId",
                                                        "failed-operation",
                                                        "expectedActiveRevision",
                                                        "wrong"))))
                .andExpect(status().isConflict());
        assertEquals(pending, store.readModels().get("pendingRevision"));
        String body =
                json.writeValueAsString(
                        Map.of(
                                "operationId",
                                "failed-operation",
                                "expectedActiveRevision",
                                active));
        var first =
                mvc.perform(
                                post("/api/setup/recovery/rollback")
                                        .cookie(cookie)
                                        .header("Origin", "http://localhost:5173")
                                        .header("X-CSRF-Token", csrf)
                                        .header("Idempotency-Key", "one-rollback")
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse();
        Object target = store.readModels().get("pendingRevision");
        var second =
                mvc.perform(
                                post("/api/setup/recovery/rollback")
                                        .cookie(cookie)
                                        .header("Origin", "http://localhost:5173")
                                        .header("X-CSRF-Token", csrf)
                                        .header("Idempotency-Key", "one-rollback")
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse();
        assertEquals(
                json.readTree(first.getContentAsString()).at("/data/operationId"),
                json.readTree(second.getContentAsString()).at("/data/operationId"));
        assertEquals(target, store.readModels().get("pendingRevision"));
    }
}
