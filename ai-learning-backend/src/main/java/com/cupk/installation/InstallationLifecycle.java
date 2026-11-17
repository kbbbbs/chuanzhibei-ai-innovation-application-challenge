package com.cupk.installation;

import com.cupk.aiacademy.installation.InstallationConfigStore;
import com.cupk.aiacademy.installation.InstallationStateStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ConfigurableApplicationContext;

/** Private on-disk operation ledger shared by the supervisor and its single child. */
public final class InstallationLifecycle {
    public static final int APPLY_EXIT_CODE = 42;
    public static final long DRAIN_SECONDS = 30;
    public static final long READY_SECONDS = 120;
    private final Path directory;
    private final InstallationConfigStore models;
    private final InstallationStateStore installation;
    private final boolean applicationChild;
    private final boolean managedChild;
    private final AtomicBoolean draining = new AtomicBoolean();

    public InstallationLifecycle(Path directory, Map<String, String> environment) {
        this.directory = directory.toAbsolutePath().normalize();
        this.models = new InstallationConfigStore(this.directory, environment);
        this.installation = new InstallationStateStore(this.directory, environment);
        this.applicationChild = "APP".equals(environment.get("AI_INSTALLATION_CHILD"));
        this.managedChild = Set.of("APP", "BOOTSTRAP").contains(environment.getOrDefault("AI_INSTALLATION_CHILD", ""))
                && environment.containsKey("AI_INSTALLATION_PARENT_PID")
                && environment.containsKey("AI_INSTALLATION_PARENT_STARTED");
    }

    public String installationId() { return installation.installationId(); }

    public Map<String, Object> requestApply(String target, String expectedActive, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200)
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("REVISION_REQUIRED");
        String keyHash = digest(idempotencyKey);
        return InstallationFiles.locked(directory, ledger -> {
            var operations = operations(ledger);
            for (Map<String, Object> operation : operations.values()) {
                if (keyHash.equals(operation.get("idempotencyHash"))) {
                    if (!Objects.equals(target, operation.get("targetRevision"))
                            || !Objects.equals(expectedActive, operation.get("expectedActiveRevision")))
                        throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
                    return summary(operation);
                }
            }
            Map<String, Object> current = latest(ledger);
            if (current != null && running(current)) throw new IllegalStateException("OPERATION_IN_PROGRESS");
            Map<String, Object> settings = models.readModels();
            if (!Objects.equals(settings.get("activeRevision"), expectedActive)
                    || !Objects.equals(settings.get("pendingRevision"), target))
                throw new IllegalStateException("REVISION_CONFLICT");
            String now = Instant.now().toString();
            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("operationId", UUID.randomUUID().toString());
            operation.put("installationId", installationId());
            operation.put("targetRevision", target);
            Object confirmed = ledger.get("confirmedActiveRevision");
            operation.put("expectedActiveRevision", expectedActive);
            operation.put("previousRevision", confirmed);
            operation.put("activeRevision", confirmed);
            operation.put("status", "ACCEPTED");
            operation.put("phase", "SAVE");
            operation.put("startedAt", now);
            operation.put("updatedAt", now);
            operation.put("error", null);
            operation.put("canRetry", false);
            operation.put("canRollback", confirmed != null);
            operation.put("idempotencyHash", keyHash);
            operations.put((String) operation.get("operationId"), operation);
            ledger.put("currentOperationId", operation.get("operationId"));
            return summary(operation);
        });
    }

    public Map<String, Object> getOperation(String id) {
        return InstallationFiles.locked(directory, ledger -> {
            var operation = operations(ledger).get(id);
            if (operation == null) throw new IllegalArgumentException("OPERATION_NOT_FOUND");
            Map<String, Object> result = new LinkedHashMap<>(operation);
            result.remove("idempotencyHash");
            result.remove("expectedActiveRevision");
            return result;
        });
    }

    public String state() {
        return InstallationFiles.locked(directory, ledger -> {
            Map<String, Object> current = latest(ledger);
            if (current != null && "RECOVERY_REQUIRED".equals(current.get("status"))) return "RECOVERY_REQUIRED";
            if (current != null && running(current)) return "PENDING_RESTART";
            if (Boolean.TRUE.equals(ledger.get("legacyReady"))) return "READY";
            if (applicationChild && !installation.installed()) return "PENDING_RESTART";
            return installation.installed() ? "READY" : "UNINITIALIZED";
        });
    }

    /** Call only after flushing the accepted HTTP response. */
    public void beginDrain(ConfigurableApplicationContext context) {
        if (!managedChild) throw new IllegalStateException("LIFECYCLE_UNSUPPORTED");
        if (!draining.compareAndSet(false, true)) return;
        InstallationFiles.locked(directory, ledger -> {
            Map<String, Object> operation = latest(ledger);
            if (operation == null || !"ACCEPTED".equals(operation.get("status")))
                throw new IllegalStateException("NO_ACCEPTED_OPERATION");
            update(operation, "DRAINING", "DRAIN");
            return null;
        });
        Thread deadline = new Thread(() -> {
            try { Thread.sleep(DRAIN_SECONDS * 1000); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            Runtime.getRuntime().halt(APPLY_EXIT_CODE);
        }, "installation-drain-deadline");
        deadline.setDaemon(true);
        deadline.start();
        Thread shutdown = new Thread(() -> {
            try { context.close(); }
            finally { System.exit(APPLY_EXIT_CODE); }
        }, "installation-drain");
        shutdown.setDaemon(false);
        shutdown.start();
    }

    Map<String, Object> current() {
        return InstallationFiles.locked(directory, ledger -> {
            var operation = latest(ledger);
            return operation == null ? null : new LinkedHashMap<>(operation);
        });
    }

    Map<String, Object> claim() {
        return InstallationFiles.locked(directory, ledger -> {
            var operation = latest(ledger);
            if (operation == null) return null;
            String status = (String) operation.get("status");
            if (!"ACCEPTED".equals(status) && !"DRAINING".equals(status)) return null;
            if (!Objects.equals(models.readModels().get("pendingRevision"), operation.get("targetRevision"))
                    || !Objects.equals(installationId(), operation.get("installationId"))) {
                update(operation, "RECOVERY_REQUIRED", "BOOT");
                operation.put("error", Map.of("code", "LAUNCH_STATE_CONFLICT",
                        "message", "The saved launch state no longer matches the accepted operation."));
                operation.put("canRetry", true);
                return null;
            }
            update(operation, "STARTING", "BOOT");
            return new LinkedHashMap<>(operation);
        });
    }

    void applied(String id, String instance, String revision) {
        InstallationFiles.locked(directory, ledger -> {
            var operation = operations(ledger).get(id);
            if (operation == null || !"STARTING".equals(operation.get("status"))
                    || !Objects.equals(instance, operation.get("installationId"))
                    || !Objects.equals(revision, operation.get("targetRevision")))
                throw new IllegalStateException("READY_HANDSHAKE_MISMATCH");
            models.activate(revision);
            installation.confirmRevision(revision);
            ledger.put("confirmedActiveRevision", revision);
            operation.put("activeRevision", revision);
            operation.put("error", null);
            operation.put("canRollback", operation.get("previousRevision") != null);
            update(operation, "APPLIED", "READY");
            return null;
        });
    }

    void confirmed(String revision) {
        InstallationFiles.locked(directory, ledger -> {
            ledger.put("confirmedActiveRevision", revision);
            if (!installation.installed()) ledger.put("legacyReady", true);
            return null;
        });
    }

    void failed(String code) {
        InstallationFiles.locked(directory, ledger -> {
            var operation = latest(ledger);
            if (operation != null && (running(operation)
                    || "RECOVERY_REQUIRED".equals(operation.get("status")))) {
                update(operation, "RECOVERY_REQUIRED", "BOOT");
                operation.put("error", Map.of("code", code, "message", "The application could not confirm readiness."));
                operation.put("canRetry", true);
            } else {
                String now = Instant.now().toString();
                var recovery = new LinkedHashMap<String, Object>();
                String id = UUID.randomUUID().toString();
                Object active = ledger.get("confirmedActiveRevision");
                recovery.put("operationId", id);
                recovery.put("installationId", installationId());
                recovery.put("targetRevision", Objects.toString(active, "environment"));
                Object previous = operation == null ? null : operation.get("previousRevision");
                recovery.put("previousRevision", previous);
                recovery.put("activeRevision", active);
                recovery.put("status", "RECOVERY_REQUIRED");
                recovery.put("phase", "BOOT");
                recovery.put("startedAt", now);
                recovery.put("updatedAt", now);
                recovery.put("error", Map.of("code", code, "message", "The application could not confirm readiness."));
                recovery.put("canRetry", true);
                recovery.put("canRollback", previous != null);
                operations(ledger).put(id, recovery);
                ledger.put("currentOperationId", id);
            }
            return null;
        });
    }

    private static boolean running(Map<String, Object> operation) {
        return Set.of("ACCEPTED", "DRAINING", "STARTING").contains(operation.get("status"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> operations(Map<String, Object> ledger) {
        return (Map<String, Map<String, Object>>) ledger.computeIfAbsent("operations", key -> new LinkedHashMap<>());
    }

    private static Map<String, Object> latest(Map<String, Object> ledger) {
        return operations(ledger).get(ledger.get("currentOperationId"));
    }

    private static void update(Map<String, Object> operation, String status, String phase) {
        operation.put("status", status);
        operation.put("phase", phase);
        operation.put("updatedAt", Instant.now().toString());
    }

    private static Map<String, Object> summary(Map<String, Object> operation) {
        String id = (String) operation.get("operationId");
        return Map.of("operationId", id, "targetRevision", operation.get("targetRevision"),
                "status", operation.get("status"), "statusUrl", "/api/setup/operations/" + id);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA256_UNAVAILABLE");
        }
    }
}
