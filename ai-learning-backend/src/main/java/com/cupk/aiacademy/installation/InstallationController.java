package com.cupk.aiacademy.installation;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.installation.InstallationLifecycle;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "installation.lifecycle.enabled",
        havingValue = "true")
public class InstallationController {
    private final InstallationStateStore state;
    private final InstallationConfigStore models;
    private final InstallationSessions sessions;
    private final DatabaseInstaller database;
    private final DatabaseDraftResolver resolver;
    private final InstallationLifecycle lifecycle;
    private final DraftModelProbeService probes;
    private final ObjectProvider<AuthUtils> authentication;
    private final ConfigurableApplicationContext context;
    private final Map<String, DatabaseCheck> checks = new ConcurrentHashMap<>();

    private record DatabaseCheck(
            String session, String digest, String classification, Instant expiresAt) {}

    public InstallationController(
            InstallationStateStore state,
            InstallationConfigStore models,
            InstallationSessions sessions,
            DatabaseInstaller database,
            DatabaseDraftResolver resolver,
            InstallationLifecycle lifecycle,
            DraftModelProbeService probes,
            ObjectProvider<AuthUtils> authentication,
            ConfigurableApplicationContext context) {
        this.state = state;
        this.models = models;
        this.sessions = sessions;
        this.database = database;
        this.resolver = resolver;
        this.lifecycle = lifecycle;
        this.probes = probes;
        this.authentication = authentication;
        this.context = context;
    }

    @GetMapping("/api/setup/status")
    public ResponseEntity<?> status() {
        String value = lifecycle.state();
        return ok(
                Map.of(
                        "state",
                        value,
                        "setupAllowed",
                        !state.installed()
                                && Set.of("UNINITIALIZED", "CONFIGURING").contains(value),
                        "recoveryRequired",
                        "RECOVERY_REQUIRED".equals(value)));
    }

    @PostMapping("/api/setup/session")
    public ResponseEntity<?> exchange(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireUninstalled();
        return ok(sessions.exchange(text(body, "code"), "INSTALL", request, response));
    }

    @GetMapping("/api/setup/session")
    public ResponseEntity<?> current(HttpServletRequest request) {
        return ok(sessions.current(request, "INSTALL"));
    }

    @PostMapping("/api/setup/database/check")
    public ResponseEntity<?> check(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireUninstalled();
        String session = sessions.requireInstall(request);
        var settings = resolver.resolve(object(body, "database"));
        var classification = database.check(settings, state.installationId());
        String wire = wire(classification);
        String id = UUID.randomUUID().toString();
        Instant expires = Instant.now().plusSeconds(300);
        checks.put(id, new DatabaseCheck(session, digest(settings), wire, expires));
        return ok(
                Map.of(
                        "checkId",
                        id,
                        "expiresAt",
                        expires.toString(),
                        "classification",
                        wire,
                        "compatible",
                        Set.of(
                                        DatabaseInstaller.Classification.EMPTY,
                                        DatabaseInstaller.Classification.LEGACY)
                                .contains(classification),
                        "requiresExistingAdmin",
                        classification == DatabaseInstaller.Classification.LEGACY));
    }

    @PostMapping("/api/setup/models/probe")
    public ResponseEntity<?> setupProbe(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireUninstalled();
        sessions.requireInstall(request);
        return ok(probes.probe(text(body, "role"), roles(body)));
    }

    @PostMapping("/api/setup/complete")
    public synchronized ResponseEntity<?> complete(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request,
            HttpServletResponse response) {
        String session = sessions.requireInstallReplay(request);
        if (key.isBlank() || key.length() > 128)
            throw new InstallationException(400, "IDEMPOTENCY_KEY_INVALID");
        String requestHash = requestHash(body);
        var receipt = state.completion();
        boolean replay = !receipt.isEmpty() && hash(key).equals(receipt.get("keyHash"));
        if (replay) {
            if (!requestHash.equals(receipt.get("requestHash"))
                    || !session.equals(receipt.get("session")))
                throw new InstallationException(409, "IDEMPOTENCY_CONFLICT");
            if (receipt.get("operationId") != null) {
                var operation = lifecycle.getOperation(String.valueOf(receipt.get("operationId")));
                sessions.grantRecovery(
                        request, response, text(operation, "operationId"), state.installationId());
                if ("ACCEPTED".equals(operation.get("status"))) scheduleDrain(request);
                return accepted(summary(operation));
            }
        } else if (!receipt.isEmpty()
                && (state.installed() || !session.equals(receipt.get("session"))))
            throw new InstallationException(409, "INSTALLATION_ALREADY_STARTED");
        if (!replay) requireUninstalled();
        DatabaseCheck check = checks.get(text(body, "checkId"));
        var settings = resolver.resolve(object(body, "database"));
        if (!replay
                && (check == null
                        || check.expiresAt().isBefore(Instant.now())
                        || !check.session().equals(session)
                        || !check.digest().equals(digest(settings))))
            throw new InstallationException(409, "DATABASE_CHECK_EXPIRED");
        String mode = text(body, "mode");
        if (!replay
                && (("NEW".equals(mode) && !"EMPTY".equals(check.classification()))
                        || ("ADOPT".equals(mode)
                                && !"KNOWN_EXISTING".equals(check.classification()))))
            throw new InstallationException(409, "DATABASE_CLASSIFICATION_CHANGED");
        if (body.get("expectedRevision") != null)
            throw new InstallationException(409, "REVISION_CONFLICT");
        var drafts = roles(body);
        for (String role : List.of("generation", "planning", "embedding", "ocr"))
            models.resolveDraft(role, drafts);
        if ("ADOPT".equals(mode) && body.containsKey("admin"))
            throw new InstallationException(422, "ADOPTION_ADMIN_FORBIDDEN");
        var administrator = stringObject(body, "NEW".equals(mode) ? "admin" : "existingAdmin");
        database.validateCredentials(settings, mode, administrator);
        Map<String, Object> saved;
        if (!replay) {
            saved =
                    models.saveModels(
                            String.valueOf(models.readModels().get("editableRevision")), drafts);
            receipt =
                    new LinkedHashMap<>(
                            Map.of(
                                    "keyHash",
                                    hash(key),
                                    "requestHash",
                                    requestHash,
                                    "session",
                                    session,
                                    "targetRevision",
                                    saved.get("pendingRevision"),
                                    "previousRevision",
                                    models.readModels().get("activeRevision")));
            state.saveCompletion(receipt);
        }
        if (!state.installed()) {
            database.install(settings, mode, state.installationId(), administrator);
            state.markInstalled(settings, state.installationId());
        } else database.verifyIdentity(settings, state.installationId());
        state.stageDatabase(String.valueOf(receipt.get("targetRevision")), settings);
        var operation =
                lifecycle.requestApply(
                        String.valueOf(receipt.get("targetRevision")),
                        String.valueOf(receipt.get("previousRevision")),
                        key);
        receipt.put("operationId", operation.get("operationId"));
        state.saveCompletion(receipt);
        sessions.grantRecovery(
                request,
                response,
                String.valueOf(operation.get("operationId")),
                state.installationId());
        scheduleDrain(request);
        return accepted(operation);
    }

    @GetMapping({"/api/setup/operations/{id}", "/api/admin/settings/operations/{id}"})
    public ResponseEntity<?> operation(@PathVariable String id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            try {
                var authorization = sessions.current(request, "RECOVERY");
                if (!id.equals(authorization.get("operationId")))
                    throw new InstallationException(403, "OPERATION_SCOPE_INVALID");
            } catch (InstallationException failure) {
                String session = sessions.requireInstall(request);
                var receipt = state.completion();
                if (!session.equals(receipt.get("session"))
                        || !id.equals(receipt.get("operationId")))
                    throw new InstallationException(403, "OPERATION_SCOPE_INVALID");
            }
        }
        return ok(lifecycle.getOperation(id));
    }

    @GetMapping("/api/admin/settings/models")
    public ResponseEntity<?> models(HttpServletRequest request) {
        requireAdmin(request);
        return ok(models.readModels());
    }

    @PutMapping("/api/admin/settings/models")
    public synchronized ResponseEntity<?> save(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireAdmin(request);
        sessions.verifyOrigin(request);
        if ("PENDING_RESTART".equals(lifecycle.state()))
            throw new InstallationException(409, "OPERATION_IN_PROGRESS");
        var saved = models.saveModels(text(body, "expectedRevision"), roles(body));
        state.copyDatabase(
                text(body, "expectedRevision"), String.valueOf(saved.get("pendingRevision")));
        return ok(saved);
    }

    @PostMapping("/api/admin/settings/models/probe")
    public ResponseEntity<?> probe(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireAdmin(request);
        sessions.verifyOrigin(request);
        return ok(probes.probe(text(body, "role"), roles(body)));
    }

    @PostMapping("/api/admin/settings/apply")
    public synchronized ResponseEntity<?> apply(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireAdmin(request);
        sessions.verifyOrigin(request);
        var operation =
                lifecycle.requestApply(
                        text(body, "revision"), text(body, "expectedActiveRevision"), key);
        sessions.grantRecovery(
                request, response, text(operation, "operationId"), state.installationId());
        scheduleDrain(request);
        Map<String, Object> result = new LinkedHashMap<>(operation);
        result.put("statusUrl", "/api/admin/settings/operations/" + operation.get("operationId"));
        return accepted(result);
    }

    @PostMapping("/api/setup/recovery/session")
    public ResponseEntity<?> recoverySession(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!"RECOVERY_REQUIRED".equals(lifecycle.state()))
            throw new InstallationException(409, "RECOVERY_NOT_REQUIRED");
        return ok(sessions.exchange(text(body, "code"), "RECOVERY", request, response));
    }

    @GetMapping("/api/setup/recovery/session")
    public ResponseEntity<?> recoveryCurrent(HttpServletRequest request) {
        return ok(sessions.current(request, "RECOVERY"));
    }

    @GetMapping("/api/setup/recovery/config")
    public ResponseEntity<?> recoveryConfig(HttpServletRequest request) {
        var session = sessions.current(request, "RECOVERY");
        sessions.requireRecovery(request, String.valueOf(session.get("operationId")));
        requireRecoveryState();
        var view = models.readModels();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", session.get("operationId"));
        result.put("expectedRevision", view.get("editableRevision"));
        result.put("roles", view.get("roles"));
        result.put("database", state.databaseView(String.valueOf(view.get("editableRevision"))));
        result.put(
                "previousRevision",
                lifecycle
                        .getOperation(String.valueOf(session.get("operationId")))
                        .get("previousRevision"));
        return ok(result);
    }

    @PutMapping("/api/setup/recovery/config")
    public synchronized ResponseEntity<?> recoverySave(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        sessions.requireRecovery(request, text(body, "operationId"));
        requireRecoveryState();
        DatabaseInstaller.DatabaseSettings settings = null;
        if (body.containsKey("database")) {
            settings = resolver.resolve(object(body, "database"), text(body, "expectedRevision"));
            database.verifyIdentity(settings, state.installationId());
        }
        var saved = models.saveModels(text(body, "expectedRevision"), roles(body));
        String revision = String.valueOf(saved.get("pendingRevision"));
        if (settings != null) state.stageDatabase(revision, settings);
        else state.copyDatabase(text(body, "expectedRevision"), revision);
        return ok(saved);
    }

    @PostMapping("/api/setup/recovery/apply")
    public synchronized ResponseEntity<?> recoveryApply(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request,
            HttpServletResponse response) {
        var replay = replayRecovery(body, key, request, response);
        if (replay != null) return accepted(replay);
        sessions.requireRecovery(request, text(body, "operationId"));
        requireRecoveryState();
        var editable = models.readModels();
        if (!Objects.equals(editable.get("activeRevision"), body.get("expectedActiveRevision"))
                || !Objects.equals(editable.get("pendingRevision"), body.get("revision")))
            throw new InstallationException(409, "REVISION_CONFLICT");
        state.saveRecoveryReceipt(
                hash(key),
                new LinkedHashMap<>(
                        Map.of(
                                "requestHash",
                                recoveryHash(body, request),
                                "parentOperationId",
                                body.get("operationId"),
                                "targetRevision",
                                text(body, "revision"),
                                "expectedActiveRevision",
                                text(body, "expectedActiveRevision"))));
        var operation =
                lifecycle.requestApply(
                        text(body, "revision"), text(body, "expectedActiveRevision"), key);
        finishRecoveryReceipt(body, key, operation);
        sessions.grantRecovery(
                request, response, text(operation, "operationId"), state.installationId());
        sessions.revokeRecovery(text(body, "operationId"));
        scheduleDrain(request);
        return accepted(operation);
    }

    @PostMapping("/api/setup/recovery/rollback")
    public synchronized ResponseEntity<?> rollback(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request,
            HttpServletResponse response) {
        var replay = replayRecovery(body, key, request, response);
        if (replay != null) return accepted(replay);
        sessions.requireRecovery(request, text(body, "operationId"));
        requireRecoveryState();
        if (!Objects.equals(
                models.readModels().get("activeRevision"), body.get("expectedActiveRevision")))
            throw new InstallationException(409, "REVISION_CONFLICT");
        var old = lifecycle.getOperation(text(body, "operationId"));
        if (old.get("previousRevision") == null)
            throw new InstallationException(409, "ROLLBACK_UNAVAILABLE");
        models.rollbackTo(String.valueOf(old.get("previousRevision")));
        var view = models.readModels();
        state.copyDatabase(
                String.valueOf(old.get("previousRevision")),
                String.valueOf(view.get("pendingRevision")));
        state.saveRecoveryReceipt(
                hash(key),
                new LinkedHashMap<>(
                        Map.of(
                                "requestHash",
                                recoveryHash(body, request),
                                "parentOperationId",
                                body.get("operationId"),
                                "targetRevision",
                                view.get("pendingRevision"),
                                "expectedActiveRevision",
                                text(body, "expectedActiveRevision"))));
        var operation =
                lifecycle.requestApply(
                        String.valueOf(view.get("pendingRevision")),
                        text(body, "expectedActiveRevision"),
                        key);
        finishRecoveryReceipt(body, key, operation);
        sessions.grantRecovery(
                request, response, text(operation, "operationId"), state.installationId());
        sessions.revokeRecovery(text(body, "operationId"));
        scheduleDrain(request);
        return accepted(operation);
    }

    private void scheduleDrain(HttpServletRequest request) {
        request.setAttribute("installation.beginDrain", Boolean.TRUE);
    }

    private boolean isAdmin(HttpServletRequest request) {
        AuthUtils auth = authentication.getIfAvailable();
        if (auth == null) return false;
        try {
            return auth.isAdmin(auth.requireLogin(request.getHeader("Authorization")));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!isAdmin(request))
            throw new InstallationException(
                    request.getHeader("Authorization") == null ? 401 : 403, "ADMIN_REQUIRED");
    }

    private void requireUninstalled() {
        if (state.installed() || "READY".equals(lifecycle.state()))
            throw new InstallationException(409, "INSTALLATION_ALREADY_COMPLETED");
    }

    private void requireRecoveryState() {
        if (!"RECOVERY_REQUIRED".equals(lifecycle.state()))
            throw new InstallationException(409, "RECOVERY_NOT_REQUIRED");
    }

    private static String wire(DatabaseInstaller.Classification value) {
        return switch (value) {
            case EMPTY -> "EMPTY";
            case LEGACY -> "KNOWN_EXISTING";
            case MANAGED -> "MANAGED";
            case UNKNOWN -> "UNKNOWN_NONEMPTY";
        };
    }

    private static String digest(DatabaseInstaller.DatabaseSettings settings) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (settings.url()
                                                            + "\0"
                                                            + settings.username()
                                                            + "\0"
                                                            + settings.password())
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("CHECK_UNAVAILABLE");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("DIGEST_UNAVAILABLE");
        }
    }

    private static String requestHash(Map<String, Object> value) {
        try {
            return hash(
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .configure(
                                    com.fasterxml.jackson.databind.SerializationFeature
                                            .ORDER_MAP_ENTRIES_BY_KEYS,
                                    true)
                            .writeValueAsString(value));
        } catch (Exception e) {
            throw new InstallationException(400, "REQUEST_INVALID");
        }
    }

    private static Map<String, Object> summary(Map<String, Object> operation) {
        return Map.of(
                "operationId",
                operation.get("operationId"),
                "targetRevision",
                operation.get("targetRevision"),
                "status",
                operation.get("status"),
                "statusUrl",
                "/api/setup/operations/" + operation.get("operationId"));
    }

    private Map<String, Object> replayRecovery(
            Map<String, Object> body,
            String key,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw new InstallationException(400, "IDEMPOTENCY_KEY_INVALID");
        var receipt = state.recoveryReceipt(hash(key));
        if (receipt.isEmpty()) return null;
        var authorization = sessions.current(request, "RECOVERY");
        String scope = String.valueOf(authorization.get("operationId"));
        sessions.requireRecoveryReplay(request, scope);
        if (!recoveryHash(body, request).equals(receipt.get("requestHash"))
                || (!scope.equals(receipt.get("parentOperationId"))
                        && !scope.equals(receipt.get("operationId"))))
            throw new InstallationException(409, "IDEMPOTENCY_CONFLICT");
        Map<String, Object> operation;
        if (receipt.get("operationId") != null)
            operation = lifecycle.getOperation(String.valueOf(receipt.get("operationId")));
        else {
            operation =
                    lifecycle.requestApply(
                            String.valueOf(receipt.get("targetRevision")),
                            String.valueOf(receipt.get("expectedActiveRevision")),
                            key);
            finishRecoveryReceipt(body, key, operation);
        }
        sessions.grantRecovery(
                request, response, text(operation, "operationId"), state.installationId());
        sessions.revokeRecovery(text(body, "operationId"));
        if ("ACCEPTED".equals(operation.get("status"))) scheduleDrain(request);
        return summary(operation);
    }

    private void finishRecoveryReceipt(
            Map<String, Object> body, String key, Map<String, Object> operation) {
        var receipt = new LinkedHashMap<>(state.recoveryReceipt(hash(key)));
        receipt.put("operationId", operation.get("operationId"));
        state.saveRecoveryReceipt(hash(key), receipt);
    }

    private static String recoveryHash(Map<String, Object> body, HttpServletRequest request) {
        return requestHash(Map.of("path", request.getRequestURI(), "body", body));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> body, String name) {
        if (!(body.get(name) instanceof Map<?, ?>))
            throw new InstallationException(422, "REQUEST_FIELD_REQUIRED");
        return (Map<String, Object>) body.get(name);
    }

    private static Map<String, String> stringObject(Map<String, Object> body, String name) {
        Map<String, String> result = new LinkedHashMap<>();
        object(body, name).forEach((key, value) -> result.put(key, String.valueOf(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> roles(Map<String, Object> body) {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) object(body, "roles");
    }

    private static String text(Map<String, ?> body, String name) {
        return String.valueOf(body.getOrDefault(name, null) == null ? "" : body.get(name));
    }

    private static ResponseEntity<?> ok(Object data) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("code", "200", "msg", "成功", "data", data));
    }

    private static ResponseEntity<?> accepted(Object data) {
        return ResponseEntity.accepted()
                .header("Cache-Control", "no-store")
                .body(Map.of("code", "202", "msg", "已接受", "data", data));
    }
}
