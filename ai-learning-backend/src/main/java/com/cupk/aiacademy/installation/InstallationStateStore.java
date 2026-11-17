package com.cupk.aiacademy.installation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Private installation identity is independent of database availability. */
public final class InstallationStateStore {
    private final Path file;
    private final Map<String, String> environment;
    private final ObjectMapper json = new ObjectMapper();

    public InstallationStateStore(Path directory, Map<String, String> environment) {
        this.file = directory.resolve("installation-state.json");
        this.environment = Map.copyOf(environment);
        if (!Files.exists(file))
            write(
                    new LinkedHashMap<>(
                            Map.of(
                                    "installationId",
                                    UUID.randomUUID().toString(),
                                    "installed",
                                    false)));
    }

    public synchronized Map<String, Object> read() {
        try {
            return json.readValue(Files.readAllBytes(file), new TypeReference<>() {});
        } catch (Exception e) {
            throw new InstallationException(503, "INSTALLATION_STATE_UNAVAILABLE");
        }
    }

    public String installationId() {
        return String.valueOf(read().get("installationId"));
    }

    public boolean installed() {
        return Boolean.TRUE.equals(read().get("installed"));
    }

    public synchronized void saveCompletion(Map<String, Object> completion) {
        var values = read();
        values.put("completion", completion);
        write(values);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> completion() {
        return read().get("completion") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> recoveryReceipt(String keyHash) {
        var all = read().get("recoveryReceipts");
        if (all instanceof Map<?, ?> receipts && receipts.get(keyHash) instanceof Map<?, ?> receipt)
            return new LinkedHashMap<>((Map<String, Object>) receipt);
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public synchronized void saveRecoveryReceipt(String keyHash, Map<String, Object> receipt) {
        var values = read();
        var all =
                values.get("recoveryReceipts") instanceof Map<?, ?> map
                        ? new LinkedHashMap<>((Map<String, Object>) map)
                        : new LinkedHashMap<String, Object>();
        all.put(keyHash, receipt);
        values.put("recoveryReceipts", all);
        write(values);
    }

    public synchronized void markInstalled(
            DatabaseInstaller.DatabaseSettings database, String installationId) {
        Map<String, Object> state = read();
        if (!installationId().equals(installationId))
            throw new InstallationException(409, "INSTALLATION_ID_MISMATCH");
        state.put("installed", true);
        state.put(
                "database",
                Map.of(
                        "url",
                        database.url(),
                        "username",
                        database.username(),
                        "password",
                        database.password()));
        state.putIfAbsent("jwtSecret", UUID.randomUUID().toString() + UUID.randomUUID());
        write(state);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> exportWorkerProperties(String revision) {
        Map<String, Object> state = read();
        Map<String, String> values = new LinkedHashMap<>();
        if (databaseFor(state, revision) instanceof Map<?, ?> db) {
            String url =
                    environment.getOrDefault(
                            "SPRING_DATASOURCE_URL",
                            environment.getOrDefault("DB_URL", String.valueOf(db.get("url"))));
            values.put("spring.datasource.url", url);
            values.put(
                    "spring.datasource.username",
                    environment.getOrDefault(
                            "SPRING_DATASOURCE_USERNAME",
                            environment.getOrDefault(
                                    "DB_USERNAME", String.valueOf(db.get("username")))));
            values.put(
                    "spring.datasource.password",
                    environment.getOrDefault(
                            "SPRING_DATASOURCE_PASSWORD",
                            environment.getOrDefault(
                                    "DB_PASSWORD", String.valueOf(db.get("password")))));
            values.put(
                    "spring.datasource.driver-class-name",
                    url.startsWith("jdbc:h2:") ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        }
        values.put(
                "jwt.secret",
                environment.getOrDefault(
                        "JWT_SECRET", String.valueOf(state.getOrDefault("jwtSecret", ""))));
        return values;
    }

    public Map<String, String> workerProperties() {
        return exportWorkerProperties(environment.getOrDefault("AI_INSTALLATION_REVISION", ""));
    }

    public synchronized void stageDatabase(
            String revision, DatabaseInstaller.DatabaseSettings database) {
        var values = read();
        var snapshots = snapshots(values);
        snapshots.put(
                revision,
                Map.of(
                        "url",
                        database.url(),
                        "username",
                        database.username(),
                        "password",
                        database.password()));
        values.put("databaseRevisions", snapshots);
        write(values);
    }

    public synchronized void copyDatabase(String sourceRevision, String targetRevision) {
        var values = read();
        var database = databaseFor(values, sourceRevision);
        if (database == null) return;
        var snapshots = snapshots(values);
        snapshots.put(targetRevision, database);
        values.put("databaseRevisions", snapshots);
        write(values);
    }

    public synchronized void confirmRevision(String revision) {
        var values = read();
        var database = databaseFor(values, revision);
        if (database == null) return;
        values.put("database", database);
        var snapshots = snapshots(values);
        snapshots.put(revision, database);
        values.put("databaseRevisions", snapshots);
        values.put("activeDatabaseRevision", revision);
        write(values);
    }

    private Object databaseFor(Map<String, Object> values, String revision) {
        var snapshots = snapshots(values);
        return snapshots.containsKey(revision) ? snapshots.get(revision) : values.get("database");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshots(Map<String, Object> values) {
        return values.get("databaseRevisions") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    public Map<String, Object> databaseView(String revision) {
        var result = new LinkedHashMap<String, Object>();
        var locked = new ArrayList<String>();
        var sources = new LinkedHashMap<String, String>();
        result.put("mode", "EXTERNAL");
        result.put("passwordConfigured", false);
        if (databaseFor(read(), revision) instanceof Map<?, ?> database) {
            String externalUrl =
                    environment.getOrDefault("SPRING_DATASOURCE_URL", environment.get("DB_URL"));
            String url = externalUrl == null ? String.valueOf(database.get("url")) : externalUrl;
            if (url.startsWith("jdbc:mysql:")) {
                var uri = java.net.URI.create(url.substring(5));
                result.put("host", uri.getHost());
                result.put("port", uri.getPort());
                result.put("database", uri.getPath().substring(1));
                result.put(
                        "tls",
                        url.contains("sslMode=DISABLED") || url.contains("useSSL=false")
                                ? "DISABLED"
                                : "REQUIRED");
            }
            String user =
                    environment.getOrDefault(
                            "SPRING_DATASOURCE_USERNAME",
                            environment.getOrDefault(
                                    "DB_USERNAME", String.valueOf(database.get("username"))));
            result.put("username", user);
            String password =
                    environment.getOrDefault(
                            "SPRING_DATASOURCE_PASSWORD",
                            environment.getOrDefault(
                                    "DB_PASSWORD", String.valueOf(database.get("password"))));
            result.put("passwordConfigured", !password.isEmpty());
            result.put("passwordAction", "KEEP");
            if (externalUrl != null)
                locked.addAll(List.of("mode", "host", "port", "database", "tls"));
        }
        if (environment.containsKey("DB_USERNAME")
                || environment.containsKey("SPRING_DATASOURCE_USERNAME")) locked.add("username");
        if (environment.containsKey("DB_PASSWORD")
                || environment.containsKey("SPRING_DATASOURCE_PASSWORD")) locked.add("password");
        for (String field :
                List.of("mode", "host", "port", "database", "tls", "username", "password"))
            sources.put(field, locked.contains(field) ? "ENVIRONMENT" : "PRIVATE_FILE");
        result.put("lockedFields", locked);
        result.put("source", sources);
        return result;
    }

    public Map<String, Object> databaseView() {
        return databaseView("");
    }

    public DatabaseInstaller.DatabaseSettings savedDatabase(String revision) {
        if (!(databaseFor(read(), revision) instanceof Map<?, ?> database))
            throw new InstallationException(422, "DATABASE_PASSWORD_NOT_CONFIGURED");
        return new DatabaseInstaller.DatabaseSettings(
                String.valueOf(database.get("url")),
                String.valueOf(database.get("username")),
                String.valueOf(database.get("password")));
    }

    private void write(Map<String, Object> state) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "installation-state-", ".tmp");
            secure(file.getParent());
            secure(temporary);
            Files.write(temporary, json.writeValueAsBytes(state));
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new InstallationException(503, "INSTALLATION_STATE_UNAVAILABLE");
        }
    }

    private void secure(Path path) throws java.io.IOException {
        var posix =
                Files.getFileAttributeView(
                        path, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null)
            posix.setPermissions(
                    PosixFilePermissions.fromString(
                            Files.isDirectory(path) ? "rwx------" : "rw-------"));
        else {
            var acl =
                    Files.getFileAttributeView(
                            path, java.nio.file.attribute.AclFileAttributeView.class);
            if (acl != null) {
                var principal =
                        path.getFileSystem()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByName(System.getProperty("user.name"));
                acl.setAcl(
                        List.of(
                                java.nio.file.attribute.AclEntry.newBuilder()
                                        .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                                        .setPrincipal(principal)
                                        .setPermissions(
                                                EnumSet.allOf(
                                                        java.nio.file.attribute.AclEntryPermission
                                                                .class))
                                        .build()));
            }
        }
    }
}
