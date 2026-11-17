package com.cupk.aiacademy.installation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Authentication kept outside the database so a failed worker cannot erase recovery access. */
public final class InstallationSessions {
    private static final Object MONITOR = new Object();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INSTALL = "INSTALL";
    private static final String RECOVERY = "RECOVERY";
    private final Path directory;

    public InstallationSessions(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static String issueHostCode(Path directory, String purpose) {
        return issueHostCode(directory, purpose, null);
    }

    public static String issueHostCode(Path directory, String purpose, String operationId) {
        InstallationSessions sessions = new InstallationSessions(directory);
        checkPurpose(purpose);
        if (RECOVERY.equals(purpose) && (operationId == null || operationId.isBlank()))
            throw failure(409, "RECOVERY_OPERATION_REQUIRED");
        String raw = random();
        sessions.locked(data -> {
            if (INSTALL.equals(purpose)) sessions.ensureSetupAllowed();
            else sessions.ensureRecoveryOperation(operationId, true);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hash", hash(raw));
            entry.put("expiresAt", Instant.now().plusSeconds(1800).toString());
            entry.put("purpose", purpose);
            if (operationId != null) {
                entry.put("operationId", operationId);
                entry.put("installationId", sessions.installationId());
            }
            entries(data, "codes").put(purpose, entry);
            return null;
        });
        return raw;
    }

    public Map<String, Object> exchange(String code, String purpose, HttpServletRequest request,
                                        HttpServletResponse response) {
        checkPurpose(purpose);
        verifyOrigin(request);
        boolean secure = secureTransport(request);
        String raw = random();
        Map<String, Object> result = locked(data -> {
            // Rate counters are persisted even on rejected exchanges.
            long now = Instant.now().getEpochSecond();
            Map<String, Map<String, Object>> attempts = entries(data, "attempts");
            attempts.entrySet().removeIf(e -> number(e.getValue().get("until")) <= now);
            String address = hash(Objects.toString(request.getRemoteAddr(), "unknown"));
            Map<String, Object> attempt = attempts.computeIfAbsent(address,
                    ignored -> new LinkedHashMap<>(Map.of("until", now + 300, "count", 0)));
            long count = number(attempt.get("count")) + 1;
            attempt.put("count", count);
            if (count > 10) return Map.of("_error", 429);
            Map<String, Object> entry = entries(data, "codes").get(purpose);
            if (code == null || code.length() > 256 || entry == null
                    || expired(entry) || !equal(hash(code), Objects.toString(entry.get("hash"), "")))
                return Map.of("_error", 401);
            if (INSTALL.equals(purpose)) ensureSetupAllowed();
            else {
                ensureRecoveryOperation(String.valueOf(entry.get("operationId")), true);
                if (!installationId().equals(entry.get("installationId")))
                    throw failure(403, "INSTALLATION_ID_MISMATCH");
            }
            entries(data, "codes").remove(purpose);
            Map<String, Object> session = newSession(purpose, raw,
                    (String) entry.get("operationId"), (String) entry.get("installationId"));
            entries(data, "sessions").put(hash(raw), session);
            return view(session, raw);
        });
        if (result.containsKey("_error")) {
            int status = ((Number) result.get("_error")).intValue();
            throw failure(status, status == 429 ? "SESSION_RATE_LIMITED" : "INVALID_OR_EXPIRED_CODE");
        }
        cookie(response, purpose, raw, secure);
        return result;
    }

    public Map<String, Object> current(HttpServletRequest request, String purpose) {
        checkPurpose(purpose);
        return locked(data -> {
            String raw = token(request, purpose);
            return view(authenticate(data, raw, purpose), raw);
        });
    }

    public String requireInstall(HttpServletRequest request) {
        return requireInstall(request, true);
    }

    /** Only for matching a persisted completion receipt; never authorizes another installation. */
    public String requireInstallReplay(HttpServletRequest request) {
        return requireInstall(request, false);
    }

    private String requireInstall(HttpServletRequest request, boolean requireSetupAllowed) {
        return locked(data -> {
            String raw = token(request, INSTALL);
            authenticate(data, raw, INSTALL);
            if (mutating(request)) {
                verifyOrigin(request);
                csrf(request, raw);
                if (requireSetupAllowed) ensureSetupAllowed();
            }
            return hash(raw);
        });
    }

    public String requireRecovery(HttpServletRequest request, String operationId) {
        return requireRecovery(request, operationId, false);
    }

    /** Authentication for an existing receipt only; never authorizes another recovery write. */
    public String requireRecoveryReplay(HttpServletRequest request, String operationId) {
        return requireRecovery(request, operationId, true);
    }

    private String requireRecovery(HttpServletRequest request, String operationId, boolean replay) {
        return locked(data -> {
            String raw = token(request, RECOVERY);
            Map<String, Object> session = authenticate(data, raw, RECOVERY);
            if (!Objects.equals(operationId, session.get("operationId"))
                    || !installationId().equals(session.get("installationId")))
                throw failure(403, "RECOVERY_SCOPE_MISMATCH");
            if (mutating(request)) {
                verifyOrigin(request);
                csrf(request, raw);
                if (!replay) {
                    if (Boolean.TRUE.equals(session.get("writeRevoked")))
                        throw failure(403, "RECOVERY_WRITE_REVOKED");
                    ensureRecoveryOperation(operationId, true);
                }
            }
            return hash(raw);
        });
    }

    public void grantRecovery(HttpServletRequest request, HttpServletResponse response,
                              String operationId, String installationId) {
        verifyOrigin(request);
        grantRecovery(response, operationId, installationId, secureTransport(request));
    }

    /** Non-HTTP callers always receive a Secure cookie. */
    public void grantRecovery(HttpServletResponse response, String operationId, String installationId) {
        grantRecovery(response, operationId, installationId, true);
    }

    private void grantRecovery(HttpServletResponse response, String operationId, String installationId,
                               boolean secure) {
        if (operationId == null || !installationId().equals(installationId))
            throw failure(403, "RECOVERY_SCOPE_MISMATCH");
        String raw = random();
        locked(data -> {
            entries(data, "sessions").put(hash(raw),
                    newSession(RECOVERY, raw, operationId, installationId));
            return null;
        });
        cookie(response, RECOVERY, raw, secure);
    }

    public void revokeRecovery(String operationId) {
        locked(data -> {
            entries(data, "sessions").values().stream()
                    .filter(value -> Objects.equals(operationId, value.get("operationId")))
                    .forEach(value -> value.put("writeRevoked", true));
            return null;
        });
    }

    public void verifyOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.length() > 2048) throw failure(403, "UNTRUSTED_ORIGIN");
        try {
            URI uri = URI.create(origin);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()))
                throw failure(403, "UNTRUSTED_ORIGIN");
            String trusted = System.getenv("AI_INSTALLATION_TRUSTED_ORIGINS");
            if (trusted != null && !trusted.isBlank()) {
                if (Arrays.stream(trusted.split(",")).map(String::trim).noneMatch(origin::equals))
                    throw failure(403, "UNTRUSTED_ORIGIN");
            } else if (!loopback(uri.getHost())) {
                throw failure(403, "UNTRUSTED_ORIGIN");
            }
        } catch (IllegalArgumentException exception) {
            throw failure(403, "UNTRUSTED_ORIGIN");
        }
    }

    private boolean secureTransport(HttpServletRequest request) {
        if (request.isSecure()) return true;
        String trusted = System.getenv("AI_INSTALLATION_TRUSTED_ORIGINS");
        String origin = request.getHeader("Origin");
        // An explicitly configured public TLS origin permits TLS termination at a private proxy.
        if (trusted != null && origin != null && origin.startsWith("https://")
                && Arrays.stream(trusted.split(",")).map(String::trim).anyMatch(origin::equals))
            return true;
        if (trusted != null && origin != null
                && Arrays.stream(trusted.split(",")).map(String::trim).anyMatch(origin::equals)) {
            URI publicOrigin = URI.create(origin);
            if ("http".equals(publicOrigin.getScheme()) && loopback(publicOrigin.getHost())) return false;
        }
        // Forwarded headers are deliberately ignored; only direct local HTTP is exceptional.
        if (loopback(request.getServerName()) && loopback(request.getRemoteAddr())) return false;
        throw failure(403, "SECURE_TRANSPORT_REQUIRED");
    }

    private static boolean loopback(String host) {
        return host != null && Set.of("localhost", "127.0.0.1", "::1", "[::1]",
                "0:0:0:0:0:0:0:1").contains(host.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> authenticate(Map<String, Object> data, String raw, String purpose) {
        Map<String, Object> session = entries(data, "sessions").get(hash(raw));
        if (session == null || expired(session) || !purpose.equals(session.get("purpose")))
            throw failure(401, "SESSION_EXPIRED");
        return session;
    }

    private static Map<String, Object> newSession(String purpose, String raw, String operationId,
                                                   String installationId) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("purpose", purpose);
        session.put("expiresAt", Instant.now().plusSeconds(RECOVERY.equals(purpose) ? 3600 : 1800).toString());
        session.put("csrfHash", hash(csrfToken(raw)));
        if (operationId != null) session.put("operationId", operationId);
        if (installationId != null) session.put("installationId", installationId);
        return session;
    }

    private static Map<String, Object> view(Map<String, Object> session, String raw) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("expiresAt", session.get("expiresAt"));
        view.put("csrfToken", csrfToken(raw));
        if (INSTALL.equals(session.get("purpose"))) view.put("mode", INSTALL);
        else view.put("operationId", session.get("operationId"));
        return view;
    }

    private static void csrf(HttpServletRequest request, String raw) {
        if (!equal(csrfToken(raw), Objects.toString(request.getHeader("X-CSRF-Token"), "")))
            throw failure(403, "CSRF_REJECTED");
    }

    private static String csrfToken(String raw) { return hash("installation-csrf:" + raw); }
    private static boolean mutating(HttpServletRequest request) {
        return !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    }

    private static String token(HttpServletRequest request, String purpose) {
        String name = cookieName(purpose);
        String found = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) {
                if (found != null) throw failure(401, "INVALID_SESSION");
                found = cookie.getValue();
            }
        }
        if (found == null || !found.matches("[A-Za-z0-9_-]{43}"))
            throw failure(401, "SESSION_REQUIRED");
        return found;
    }

    private static void cookie(HttpServletResponse response, String purpose, String raw, boolean secure) {
        response.addHeader("Set-Cookie", cookieName(purpose) + "=" + raw
                + "; Path=" + (INSTALL.equals(purpose) ? "/backend/api/setup" : "/backend/api")
                + "; Max-Age=" + (INSTALL.equals(purpose) ? 1800 : 3600)
                + "; HttpOnly; SameSite=Strict" + (secure ? "; Secure" : ""));
        response.setHeader("Cache-Control", "no-store");
    }

    private static String cookieName(String purpose) {
        return INSTALL.equals(purpose) ? "ai_installation" : "ai_recovery";
    }

    private void ensureSetupAllowed() {
        Map<String, Object> state = read(directory.resolve("installation-state.json"));
        if (Boolean.TRUE.equals(state.get("installed")))
            throw failure(409, "INSTALLATION_ALREADY_COMPLETED");
        Map<String, Object> ledger = read(directory.resolve("operations.json"));
        if (Boolean.TRUE.equals(ledger.get("legacyReady")))
            throw failure(409, "SETUP_NOT_ALLOWED");
        Object currentId = ledger.get("currentOperationId");
        Map<String, Object> current = entries(ledger, "operations").get(currentId);
        if (current != null && !Set.of("UNINITIALIZED", "CONFIGURING").contains(
                Objects.toString(current.get("status"), "")))
            throw failure(409, "SETUP_NOT_ALLOWED");
    }

    private String installationId() {
        Object value = read(directory.resolve("installation-state.json")).get("installationId");
        if (!(value instanceof String id) || id.isBlank()) throw failure(503, "INSTALLATION_STATE_UNAVAILABLE");
        return id;
    }

    private void ensureRecoveryOperation(String operationId, boolean writable) {
        Map<String, Object> ledger = read(directory.resolve("operations.json"));
        Map<String, Object> operation = entries(ledger, "operations").get(operationId);
        if (operation == null || !installationId().equals(operation.get("installationId")))
            throw failure(403, "RECOVERY_SCOPE_MISMATCH");
        if (writable && !Objects.equals(operationId, ledger.get("currentOperationId")))
            throw failure(403, "RECOVERY_SCOPE_SUPERSEDED");
        if (writable && !"RECOVERY_REQUIRED".equals(operation.get("status")))
            throw failure(409, "RECOVERY_NOT_REQUIRED");
    }

    private <T> T locked(Function<Map<String, Object>, T> action) {
        synchronized (MONITOR) {
            try {
                Files.createDirectories(directory);
                secure(directory);
                Path lockFile = directory.resolve("sessions.lock");
                try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    secure(lockFile);
                    Path file = directory.resolve("installation-sessions.json");
                    Map<String, Object> data = read(file);
                    entries(data, "sessions").entrySet().removeIf(e -> expired(e.getValue()));
                    T result = action.apply(data);
                    Path temporary = Files.createTempFile(directory, ".sessions-", ".tmp");
                    try {
                        secure(temporary);
                        JSON.writeValue(temporary.toFile(), data);
                        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                            output.force(true);
                        }
                        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        secure(file);
                    } finally { Files.deleteIfExists(temporary); }
                    return result;
                }
            } catch (java.io.IOException exception) {
                throw failure(503, "SESSION_STORAGE_UNAVAILABLE");
            }
        }
    }

    private static Map<String, Object> read(Path path) {
        try {
            if (!Files.exists(path)) return new LinkedHashMap<>();
            if (Files.isSymbolicLink(path)) throw failure(503, "SESSION_STORAGE_UNAVAILABLE");
            return JSON.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (java.io.IOException exception) {
            throw failure(503, "SESSION_STORAGE_UNAVAILABLE");
        }
    }

    private static void secure(Path path) throws java.io.IOException {
        if (Files.isSymbolicLink(path)) throw failure(503, "SESSION_STORAGE_UNAVAILABLE");
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) posix.setPermissions(PosixFilePermissions.fromString(
                Files.isDirectory(path) ? "rwx------" : "rw-------"));
        else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> entries(Map<String, Object> data, String key) {
        return (Map<String, Map<String, Object>>) data.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
    }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    private static boolean expired(Map<String, Object> value) {
        try { return !Instant.parse(String.valueOf(value.get("expiresAt"))).isAfter(Instant.now()); }
        catch (RuntimeException exception) { return true; }
    }
    private static String random() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static boolean equal(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
    private static void checkPurpose(String purpose) {
        if (!INSTALL.equals(purpose) && !RECOVERY.equals(purpose)) throw failure(400, "INVALID_SESSION_PURPOSE");
    }
    private static InstallationException failure(int status, String code) {
        return new InstallationException(status, code);
    }
}
