package com.cupk.aiacademy.installation;

import com.cupk.aiacademy.common.PasswordUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only discovery precedes empty-schema initialization or authenticated additive adoption. */
public final class DatabaseInstaller {
    public record DatabaseSettings(String url, String username, String password) {}

    public enum Classification {
        EMPTY,
        LEGACY,
        MANAGED,
        UNKNOWN
    }

    private static final Set<String> CORE =
            Set.of("t_user", "t_role", "t_user_role", "t_course", "t_lesson");
    private static final Pattern TABLE =
            Pattern.compile("(?i)CREATE TABLE(?: IF NOT EXISTS)? [`\"]?([a-z_]+)");

    public Classification check(DatabaseSettings settings) {
        try (Connection connection = connect(settings)) {
            return classify(connection);
        } catch (SQLException e) {
            throw new InstallationException(503, "DATABASE_UNAVAILABLE");
        }
    }

    public Classification check(DatabaseSettings settings, String installationId) {
        try (Connection connection = connect(settings)) {
            if (ownedPartial(connection, installationId)) return Classification.EMPTY;
            return classify(connection);
        } catch (SQLException e) {
            throw new InstallationException(503, "DATABASE_UNAVAILABLE");
        }
    }

    public void validateCredentials(
            DatabaseSettings settings, String mode, Map<String, String> admin) {
        if ("NEW".equals(mode)) {
            validateAdmin(admin);
            return;
        }
        if (!"ADOPT".equals(mode))
            throw new InstallationException(422, "INSTALLATION_MODE_INVALID");
        try (Connection connection = connect(settings)) {
            requireExistingAdmin(connection, admin);
        } catch (SQLException error) {
            throw new InstallationException(503, "DATABASE_UNAVAILABLE");
        }
    }

    private Classification classify(Connection connection) throws SQLException {
        Set<String> tables = tables(connection);
        if (tables.isEmpty()) return Classification.EMPTY;
        if (!tables.containsAll(CORE) || !tables.containsAll(legacyTables()))
            return Classification.UNKNOWN;
        Set<String> known = knownTables();
        if (!known.containsAll(tables)) return Classification.UNKNOWN;
        if (!hasColumns(connection, "t_user", Set.of("id", "username", "password", "status"))
                || !hasColumns(connection, "t_role", Set.of("id", "role_code"))
                || !hasColumns(connection, "t_user_role", Set.of("user_id", "role_id")))
            return Classification.UNKNOWN;
        if (tables.contains("ai_installation_identity")) {
            try (Statement sql = connection.createStatement();
                    ResultSet rows =
                            sql.executeQuery("SELECT COUNT(*) FROM ai_installation_identity")) {
                rows.next();
                long count = rows.getLong(1);
                if (count > 1) return Classification.UNKNOWN;
                if (count == 1) return Classification.MANAGED;
            }
        }
        return Classification.LEGACY;
    }

    public synchronized void install(
            DatabaseSettings settings,
            String mode,
            String installationId,
            Map<String, String> admin) {
        try (Connection connection = connect(settings)) {
            boolean mysql =
                    connection
                            .getMetaData()
                            .getDatabaseProductName()
                            .toLowerCase(Locale.ROOT)
                            .contains("mysql");
            if (mysql)
                try (Statement sql = connection.createStatement();
                        ResultSet lock =
                                sql.executeQuery("SELECT GET_LOCK('ai_academy_installation',5)")) {
                    if (!lock.next() || lock.getInt(1) != 1)
                        throw new InstallationException(409, "DATABASE_INSTALLATION_BUSY");
                }
            Classification kind = classify(connection);
            if (Set.of("NEW", "ADOPT").contains(mode)
                    && kind == Classification.MANAGED
                    && identityMatches(connection, installationId)) {
                requireExistingAdmin(connection, admin);
                return;
            }
            boolean resuming = ownedPartial(connection, installationId);
            if ("NEW".equals(mode) && kind != Classification.EMPTY && !resuming)
                throw new InstallationException(409, "DATABASE_NOT_EMPTY");
            if ("ADOPT".equals(mode) && kind != Classification.LEGACY)
                throw new InstallationException(409, "DATABASE_NOT_ADOPTABLE");
            if (!Set.of("NEW", "ADOPT").contains(mode))
                throw new InstallationException(422, "INSTALLATION_MODE_INVALID");
            if ("ADOPT".equals(mode)) requireExistingAdmin(connection, admin);
            if ("NEW".equals(mode)) validateAdmin(admin);
            if ("NEW".equals(mode) || "ADOPT".equals(mode)) {
                prepareLedger(connection, installationId);
                if (mysql)
                    try (Statement sql = connection.createStatement()) {
                        sql.execute("SET FOREIGN_KEY_CHECKS=0");
                    }
                try {
                    ScriptUtils.executeSqlScript(
                            connection,
                            new ClassPathResource(
                                    mysql
                                            ? "db/installation-baseline.sql"
                                            : "contest-demo-schema.sql"));
                } finally {
                    if (mysql)
                        try (Statement sql = connection.createStatement()) {
                            sql.execute("SET FOREIGN_KEY_CHECKS=1");
                        }
                }
                try (PreparedStatement sql =
                        connection.prepareStatement(
                                "UPDATE ai_installation_migration SET completed=1 WHERE"
                                    + " installation_id=?")) {
                    sql.setString(1, installationId);
                    sql.executeUpdate();
                }
            }
            try (Statement sql = connection.createStatement()) {
                sql.execute(
                        "CREATE TABLE IF NOT EXISTS ai_installation_identity (installation_id"
                            + " VARCHAR(64) PRIMARY KEY, installed_at TIMESTAMP DEFAULT"
                            + " CURRENT_TIMESTAMP)");
            }
            connection.setAutoCommit(false);
            try {
                if ("NEW".equals(mode)) createAdmin(connection, admin);
                try (PreparedStatement sql =
                        connection.prepareStatement(
                                "INSERT INTO ai_installation_identity(installation_id)"
                                    + " VALUES(?)")) {
                    sql.setString(1, installationId);
                    sql.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (InstallationException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallationException(503, "DATABASE_INSTALLATION_FAILED");
        }
    }

    private boolean identityMatches(Connection connection, String installationId)
            throws SQLException {
        try (Statement sql = connection.createStatement();
                ResultSet rows =
                        sql.executeQuery("SELECT installation_id FROM ai_installation_identity")) {
            return rows.next() && installationId.equals(rows.getString(1)) && !rows.next();
        }
    }

    private void prepareLedger(Connection connection, String installationId) throws Exception {
        String checksum;
        try (var stream = new ClassPathResource("db/installation-baseline.sql").getInputStream()) {
            checksum =
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stream.readAllBytes()));
        }
        try (Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE IF NOT EXISTS ai_installation_migration (installation_id"
                        + " VARCHAR(64) PRIMARY KEY, checksum VARCHAR(64) NOT NULL, completed"
                        + " INTEGER NOT NULL DEFAULT 0)");
        }
        try (Statement sql = connection.createStatement();
                ResultSet rows =
                        sql.executeQuery("SELECT installation_id FROM ai_installation_migration")) {
            while (rows.next())
                if (!installationId.equals(rows.getString(1)))
                    throw new InstallationException(409, "DATABASE_INSTALLATION_OWNED");
        }
        try (PreparedStatement sql =
                connection.prepareStatement(
                        "SELECT checksum FROM ai_installation_migration WHERE installation_id=?")) {
            sql.setString(1, installationId);
            try (ResultSet rows = sql.executeQuery()) {
                if (rows.next()) {
                    if (!checksum.equals(rows.getString(1)))
                        throw new InstallationException(409, "DATABASE_BASELINE_CHANGED");
                    return;
                }
            }
        }
        try (PreparedStatement sql =
                connection.prepareStatement(
                        "INSERT INTO ai_installation_migration(installation_id,checksum,completed)"
                            + " VALUES(?,?,0)")) {
            sql.setString(1, installationId);
            sql.setString(2, checksum);
            sql.executeUpdate();
        }
    }

    private boolean ownedPartial(Connection connection, String installationId) throws SQLException {
        Set<String> names = tables(connection);
        if (!names.contains("ai_installation_migration") || !knownTables().containsAll(names))
            return false;
        try (Statement sql = connection.createStatement();
                ResultSet rows =
                        sql.executeQuery("SELECT installation_id FROM ai_installation_migration")) {
            if (!rows.next() || !installationId.equals(rows.getString(1)) || rows.next())
                return false;
        }
        if (names.contains("ai_installation_identity")) {
            try (Statement sql = connection.createStatement();
                    ResultSet rows =
                            sql.executeQuery(
                                    "SELECT installation_id FROM ai_installation_identity")) {
                if (rows.next()) return false;
            }
        }
        if (names.contains("t_user")) {
            try (Statement sql = connection.createStatement();
                    ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM t_user")) {
                rows.next();
                if (rows.getLong(1) > 0) return false;
            }
        }
        return true;
    }

    public void verifyIdentity(DatabaseSettings settings, String installationId) {
        try (Connection connection = connect(settings);
                PreparedStatement sql =
                        connection.prepareStatement(
                                "SELECT installation_id FROM ai_installation_identity")) {
            try (ResultSet rows = sql.executeQuery()) {
                if (!rows.next() || !installationId.equals(rows.getString(1)) || rows.next())
                    throw new InstallationException(409, "DATABASE_IDENTITY_MISMATCH");
            }
        } catch (SQLException e) {
            throw new InstallationException(503, "DATABASE_UNAVAILABLE");
        }
    }

    private void requireExistingAdmin(Connection connection, Map<String, String> admin)
            throws SQLException {
        if (admin == null) throw new InstallationException(403, "EXISTING_ADMIN_REQUIRED");
        try (PreparedStatement sql =
                connection.prepareStatement(
                        "SELECT u.password FROM t_user u JOIN t_user_role ur ON ur.user_id=u.id"
                            + " JOIN t_role r ON r.id=ur.role_id WHERE u.username=? AND u.status=1"
                            + " AND r.role_code='admin'")) {
            sql.setString(1, admin.get("username"));
            try (ResultSet rows = sql.executeQuery()) {
                if (!rows.next()
                        || !PasswordUtils.matches(admin.get("password"), rows.getString(1)))
                    throw new InstallationException(403, "EXISTING_ADMIN_INVALID");
            }
        }
    }

    private void validateAdmin(Map<String, String> admin) {
        if (admin == null
                || !admin.getOrDefault("username", "").matches("[A-Za-z0-9_.-]{3,50}")
                || admin.getOrDefault("password", "").length() < 12
                || admin.getOrDefault("password", "").length() > 72)
            throw new InstallationException(422, "ADMIN_CREDENTIALS_INVALID");
    }

    private void createAdmin(Connection connection, Map<String, String> admin) throws SQLException {
        for (String role : List.of("admin", "teacher", "student")) {
            try (PreparedStatement sql =
                    connection.prepareStatement(
                            "INSERT INTO t_role(role_name,role_code) VALUES(?,?)")) {
                sql.setString(1, role);
                sql.setString(2, role);
                sql.executeUpdate();
            }
        }
        try (PreparedStatement sql =
                connection.prepareStatement(
                        "INSERT INTO t_user(username,password,nickname,status) VALUES(?,?,?,1)")) {
            sql.setString(1, admin.get("username"));
            sql.setString(2, PasswordUtils.encode(admin.get("password")));
            sql.setString(3, admin.getOrDefault("nickname", admin.get("username")));
            sql.executeUpdate();
        }
        try (PreparedStatement sql =
                connection.prepareStatement(
                        "INSERT INTO t_user_role(user_id,role_id) SELECT u.id,r.id FROM t_user u"
                            + " CROSS JOIN t_role r WHERE u.username=? AND r.role_code='admin'")) {
            sql.setString(1, admin.get("username"));
            sql.executeUpdate();
        }
    }

    private Connection connect(DatabaseSettings settings) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", settings.username());
        properties.setProperty("password", settings.password());
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        return DriverManager.getConnection(settings.url(), properties);
    }

    private Set<String> tables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        String schema =
                connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")
                        ? "PUBLIC"
                        : null;
        try (ResultSet rows =
                connection
                        .getMetaData()
                        .getTables(connection.getCatalog(), schema, "%", new String[] {"TABLE"})) {
            while (rows.next()) tables.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return tables;
    }

    private boolean hasColumns(Connection connection, String table, Set<String> expected)
            throws SQLException {
        Set<String> found = new HashSet<>();
        try (ResultSet rows =
                connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
            while (rows.next())
                if (table.equalsIgnoreCase(rows.getString("TABLE_NAME")))
                    found.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        return found.containsAll(expected);
    }

    private Set<String> knownTables() {
        Set<String> tables =
                new HashSet<>(Set.of("ai_installation_identity", "ai_installation_migration"));
        for (String resource :
                List.of(
                        "db/installation-baseline.sql",
                        "contest-demo-schema.sql",
                        "db/migration/V001__rag_foundation.sql",
                        "db/migration/V002__rag_ingestion.sql",
                        "db/migration/V003__rag_formulas.sql",
                        "db/migration/V004__rag_visuals.sql",
                        "db/migration/V005__rag_parser_revision.sql")) {
            try (var stream = new ClassPathResource(resource).getInputStream()) {
                var matcher =
                        TABLE.matcher(
                                new String(
                                        stream.readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                while (matcher.find()) tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            } catch (Exception e) {
                throw new InstallationException(503, "DATABASE_SCHEMA_UNAVAILABLE");
            }
        }
        return tables;
    }

    private Set<String> legacyTables() {
        Set<String> result = new HashSet<>();
        try (var stream = new ClassPathResource("db/installation-baseline.sql").getInputStream()) {
            var matcher =
                    TABLE.matcher(
                            new String(
                                    stream.readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8));
            while (matcher.find()) result.add(matcher.group(1).toLowerCase(Locale.ROOT));
        } catch (Exception error) {
            throw new InstallationException(503, "DATABASE_SCHEMA_UNAVAILABLE");
        }
        result.removeAll(
                Set.of(
                        "t_homework_paper",
                        "t_homework_paper_question",
                        "t_homework_session",
                        "t_homework_record",
                        "t_homework_answer",
                        "t_homework_assessment_migration"));
        return result;
    }
}
