package com.cupk.aiacademy.installation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Browser input never supplies a JDBC URL or arbitrary JDBC options. */
public final class DatabaseDraftResolver {
    private final Map<String, String> environment;
    private final InstallationStateStore state;

    public DatabaseDraftResolver(Map<String, String> environment, InstallationStateStore state) {
        this.environment = environment;
        this.state = state;
    }

    public DatabaseInstaller.DatabaseSettings resolve(Map<String, Object> draft) {
        return resolve(draft, "");
    }

    public DatabaseInstaller.DatabaseSettings resolve(Map<String, Object> draft, String revision) {
        if (draft == null) throw new InstallationException(422, "DATABASE_REQUIRED");
        if (!Set.of(
                        "mode",
                        "host",
                        "port",
                        "database",
                        "username",
                        "passwordAction",
                        "passwordValue",
                        "tls")
                .containsAll(draft.keySet()))
            throw new InstallationException(422, "DATABASE_FIELD_UNKNOWN");
        boolean managed = "MANAGED".equals(draft.get("mode"));
        if (!managed && !"EXTERNAL".equals(draft.get("mode")))
            throw new InstallationException(422, "DATABASE_MODE_INVALID");
        String host =
                managed ? environment.getOrDefault("AI_MANAGED_DB_HOST", "") : text(draft, "host");
        String database =
                managed
                        ? environment.getOrDefault("AI_MANAGED_DB_NAME", "ai_academy")
                        : text(draft, "database");
        String username =
                managed
                        ? environment.getOrDefault("AI_MANAGED_DB_USERNAME", "ai_academy")
                        : text(draft, "username");
        int port;
        try {
            port =
                    Integer.parseInt(
                            managed
                                    ? environment.getOrDefault("AI_MANAGED_DB_PORT", "3306")
                                    : text(draft, "port"));
        } catch (Exception e) {
            throw new InstallationException(422, "DATABASE_PORT_INVALID");
        }
        if (!host.matches("[A-Za-z0-9.-]{1,253}")
                || !database.matches("[A-Za-z0-9_]{1,64}")
                || username.isBlank()
                || username.length() > 128
                || port < 1
                || port > 65535) throw new InstallationException(422, "DATABASE_FIELDS_INVALID");
        String password;
        if (managed) {
            String passwordFile = environment.get("AI_MANAGED_DB_PASSWORD_FILE");
            try {
                password =
                        passwordFile == null
                                ? environment.getOrDefault("AI_MANAGED_DB_PASSWORD", "")
                                : Files.readString(Path.of(passwordFile)).stripTrailing();
            } catch (Exception e) {
                throw new InstallationException(503, "MANAGED_DATABASE_CREDENTIALS_UNAVAILABLE");
            }
        } else {
            String action = text(draft, "passwordAction");
            if (!"REPLACE".equals(action) && draft.containsKey("passwordValue"))
                throw new InstallationException(422, "DATABASE_PASSWORD_ACTION_INVALID");
            password =
                    switch (action) {
                        case "REPLACE" -> {
                            String value = text(draft, "passwordValue");
                            if (value.isEmpty())
                                throw new InstallationException(422, "DATABASE_PASSWORD_REQUIRED");
                            yield value;
                        }
                        case "KEEP" -> {
                            String external =
                                    environment.getOrDefault(
                                            "SPRING_DATASOURCE_PASSWORD",
                                            environment.get("DB_PASSWORD"));
                            yield external == null
                                    ? state.savedDatabase(revision).password()
                                    : external;
                        }
                        case "CLEAR" -> {
                            if (!"true"
                                    .equalsIgnoreCase(
                                            environment.get("AI_ALLOW_EMPTY_DB_PASSWORD")))
                                throw new InstallationException(
                                        422, "DATABASE_EMPTY_PASSWORD_FORBIDDEN");
                            yield "";
                        }
                        default ->
                                throw new InstallationException(
                                        422, "DATABASE_PASSWORD_ACTION_INVALID");
                    };
        }
        String tls = text(draft, "tls");
        if (tls.isEmpty()) tls = "REQUIRED";
        if (!Set.of("REQUIRED", "DISABLED").contains(tls))
            throw new InstallationException(422, "DATABASE_TLS_INVALID");
        String url =
                "jdbc:mysql://"
                        + host
                        + ":"
                        + port
                        + "/"
                        + database
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&sslMode="
                        + tls
                        + "&connectTimeout=5000&socketTimeout=10000";
        String lockedUrl =
                environment.getOrDefault("SPRING_DATASOURCE_URL", environment.get("DB_URL"));
        String lockedUser =
                environment.getOrDefault(
                        "SPRING_DATASOURCE_USERNAME", environment.get("DB_USERNAME"));
        String lockedPassword =
                environment.getOrDefault(
                        "SPRING_DATASOURCE_PASSWORD", environment.get("DB_PASSWORD"));
        if ((lockedUrl != null && !sameConnection(lockedUrl, url))
                || (lockedUser != null && !lockedUser.equals(username))
                || (lockedPassword != null && !lockedPassword.equals(password)))
            throw new InstallationException(409, "FIELD_EXTERNALLY_MANAGED");
        return new DatabaseInstaller.DatabaseSettings(url, username, password);
    }

    private boolean sameConnection(String left, String right) {
        try {
            if (!left.startsWith("jdbc:mysql:")) return false;
            var a = java.net.URI.create(left.substring(5));
            var b = java.net.URI.create(right.substring(5));
            return a.getHost().equalsIgnoreCase(b.getHost())
                    && (a.getPort() < 0 ? 3306 : a.getPort()) == b.getPort()
                    && a.getPath().equals(b.getPath())
                    && tls(left).equals(tls(right));
        } catch (Exception error) {
            return false;
        }
    }

    private String tls(String url) {
        return url.contains("sslMode=DISABLED") || url.contains("useSSL=false")
                ? "DISABLED"
                : "REQUIRED";
    }

    private static String text(Map<String, Object> map, String key) {
        return String.valueOf(map.getOrDefault(key, ""));
    }
}
