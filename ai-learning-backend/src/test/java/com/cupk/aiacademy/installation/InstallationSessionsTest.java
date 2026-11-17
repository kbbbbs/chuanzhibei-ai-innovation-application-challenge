package com.cupk.aiacademy.installation;

import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class InstallationSessionsTest {
    @TempDir Path directory;

    @Test
    void installCodeIsSingleUseAndSecretsAreNotPersisted() throws Exception {
        InstallationSessions sessions = new InstallationSessions(directory);
        String code = InstallationSessions.issueHostCode(directory, "INSTALL");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> view = sessions.exchange(code, "INSTALL", request("POST"), response);
        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Path=/backend/api/setup"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        String token = header.substring("ai_installation=".length(), header.indexOf(';'));
        String stored = Files.readString(directory.resolve("installation-sessions.json"));
        assertFalse(stored.contains(code));
        assertFalse(stored.contains(token));
        assertFalse(stored.contains((String) view.get("csrfToken")));
        assertEquals("INVALID_OR_EXPIRED_CODE", assertThrows(InstallationException.class,
                () -> sessions.exchange(code, "INSTALL", request("POST"), new MockHttpServletResponse())).code());

        MockHttpServletRequest mutation = request("POST");
        mutation.setCookies(new Cookie("ai_installation", token));
        assertEquals("CSRF_REJECTED", assertThrows(InstallationException.class,
                () -> sessions.requireInstall(mutation)).code());
        mutation.addHeader("X-CSRF-Token", view.get("csrfToken"));
        assertNotNull(sessions.requireInstall(mutation));
        Files.writeString(directory.resolve("installation-state.json"), "{\"installed\":true}");
        assertEquals("INSTALLATION_ALREADY_COMPLETED", assertThrows(InstallationException.class,
                () -> sessions.requireInstall(mutation)).code());
        assertNotNull(sessions.requireInstallReplay(mutation));
    }

    @Test
    void recoverySessionIsScopedAndWriteRevocationDoesNotPreventReads() throws Exception {
        Files.writeString(directory.resolve("installation-state.json"), "{\"installationId\":\"install-1\"}");
        Files.writeString(directory.resolve("operations.json"),
                "{\"currentOperationId\":\"op-1\",\"operations\":{\"op-1\":{\"installationId\":\"install-1\",\"status\":\"RECOVERY_REQUIRED\"}}}");
        InstallationSessions sessions = new InstallationSessions(directory);
        String code = InstallationSessions.issueHostCode(directory, "RECOVERY", "op-1");
        assertEquals("INVALID_OR_EXPIRED_CODE", assertThrows(InstallationException.class,
                () -> sessions.exchange(code, "INSTALL", request("POST"), new MockHttpServletResponse())).code());
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> view = sessions.exchange(code, "RECOVERY", request("POST"), response);
        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Path=/backend/api;"));
        String token = header.substring("ai_recovery=".length(), header.indexOf(';'));
        MockHttpServletRequest mutation = request("POST");
        mutation.setCookies(new Cookie("ai_recovery", token));
        mutation.addHeader("X-CSRF-Token", view.get("csrfToken"));
        assertEquals("RECOVERY_SCOPE_MISMATCH", assertThrows(InstallationException.class,
                () -> sessions.requireRecovery(mutation, "op-2")).code());
        assertNotNull(sessions.requireRecovery(mutation, "op-1"));
        sessions.revokeRecovery("op-1");
        assertEquals("RECOVERY_WRITE_REVOKED", assertThrows(InstallationException.class,
                () -> sessions.requireRecovery(mutation, "op-1")).code());
        MockHttpServletRequest read = request("GET");
        read.setCookies(new Cookie("ai_recovery", token));
        assertNotNull(sessions.requireRecovery(read, "op-1"));
    }

    @Test
    void supersededRecoverySessionCannotWrite() throws Exception {
        Files.writeString(directory.resolve("installation-state.json"), "{\"installationId\":\"install-1\"}");
        Path operations = directory.resolve("operations.json");
        Files.writeString(operations,
                "{\"currentOperationId\":\"op-1\",\"operations\":{\"op-1\":{\"installationId\":\"install-1\",\"status\":\"RECOVERY_REQUIRED\"}}}");
        InstallationSessions sessions = new InstallationSessions(directory);
        String code = InstallationSessions.issueHostCode(directory, "RECOVERY", "op-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> view = sessions.exchange(code, "RECOVERY", request("POST"), response);
        String header = response.getHeader("Set-Cookie");
        String token = header.substring("ai_recovery=".length(), header.indexOf(';'));
        Files.writeString(operations,
                "{\"currentOperationId\":\"op-2\",\"operations\":{\"op-1\":{\"installationId\":\"install-1\",\"status\":\"RECOVERY_REQUIRED\"},\"op-2\":{\"installationId\":\"install-1\",\"status\":\"RECOVERY_REQUIRED\"}}}");
        MockHttpServletRequest mutation = request("POST");
        mutation.setCookies(new Cookie("ai_recovery", token));
        mutation.addHeader("X-CSRF-Token", view.get("csrfToken"));
        assertEquals("RECOVERY_SCOPE_SUPERSEDED", assertThrows(InstallationException.class,
                () -> sessions.requireRecovery(mutation, "op-1")).code());
    }

    @Test
    void legacyInstallationCannotIssueInstallCode() throws Exception {
        Files.writeString(directory.resolve("operations.json"), "{\"legacyReady\":true}");
        assertEquals("SETUP_NOT_ALLOWED", assertThrows(InstallationException.class,
                () -> InstallationSessions.issueHostCode(directory, "INSTALL")).code());
    }

    private static MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/backend/api/setup");
        request.setServerName("localhost");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Origin", "http://localhost:5173");
        return request;
    }
}
