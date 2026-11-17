package com.cupk.aiacademy.installation;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import com.cupk.aiacademy.common.PasswordUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseInstallerTest {
    private final DatabaseInstaller installer = new DatabaseInstaller();
    private final DatabaseInstaller.DatabaseSettings settings =
            new DatabaseInstaller.DatabaseSettings(
                    "jdbc:h2:mem:installation_"
                            + UUID.randomUUID().toString().replace("-", "")
                            + ";DB_CLOSE_DELAY=-1;MODE=MySQL",
                    "sa",
                    "");

    @Test
    void emptyAndUnknownSchemasAreDistinguished() throws Exception {
        assertEquals(DatabaseInstaller.Classification.EMPTY, installer.check(settings));
        execute("CREATE TABLE unrelated (id INT)");
        assertEquals(DatabaseInstaller.Classification.UNKNOWN, installer.check(settings));
        var error =
                assertThrows(
                        InstallationException.class,
                        () ->
                                installer.install(
                                        settings,
                                        "NEW",
                                        "installation-a",
                                        java.util.Map.of(
                                                "username",
                                                "admin",
                                                "password",
                                                "long-password-123")));
        assertEquals("DATABASE_NOT_EMPTY", error.code());
    }

    @Test
    void onlyTheOwnerMayResumeAnUnpopulatedPartialInstallation() throws Exception {
        execute(
                "CREATE TABLE ai_installation_migration (installation_id VARCHAR(64), checksum"
                    + " VARCHAR(64), completed INT)");
        execute("INSERT INTO ai_installation_migration VALUES ('installation-a', 'checksum', 0)");
        assertEquals(
                DatabaseInstaller.Classification.EMPTY,
                installer.check(settings, "installation-a"));
        assertEquals(
                DatabaseInstaller.Classification.UNKNOWN,
                installer.check(settings, "installation-b"));

        execute("CREATE TABLE unrelated (id INT)");
        assertEquals(
                DatabaseInstaller.Classification.UNKNOWN,
                installer.check(settings, "installation-a"));
    }

    @Test
    void partialSchemaWithExistingUsersCannotBeResumed() throws Exception {
        execute(
                "CREATE TABLE ai_installation_migration (installation_id VARCHAR(64), checksum"
                    + " VARCHAR(64), completed INT)");
        execute("INSERT INTO ai_installation_migration VALUES ('installation-a', 'checksum', 0)");
        execute("CREATE TABLE t_user (id INT)");
        execute("INSERT INTO t_user VALUES (1)");
        assertEquals(
                DatabaseInstaller.Classification.UNKNOWN,
                installer.check(settings, "installation-a"));
    }

    @Test
    void newInstallationCreatesOnlyTheRequestedAdministratorAndCanReplayAfterLocalStateLoss()
            throws Exception {
        var admin =
                Map.of("username", "owner", "password", "long-password-123", "nickname", "Owner");
        installer.install(settings, "NEW", "installation-a", admin);
        assertEquals(DatabaseInstaller.Classification.MANAGED, installer.check(settings));
        assertEquals(1, count("t_user"));
        assertEquals(3, count("t_role"));
        installer.install(settings, "NEW", "installation-a", admin);
        assertEquals(1, count("t_user"));
        assertEquals(
                "DATABASE_NOT_EMPTY",
                assertThrows(
                                InstallationException.class,
                                () ->
                                        installer.install(
                                                settings, "NEW", "foreign-installation", admin))
                        .code());
    }

    @Test
    void adoptionRequiresExistingAdministratorAndPreservesExistingUsersAcrossReplay()
            throws Exception {
        createLegacySchema();
        execute("INSERT INTO t_role(id,role_name,role_code) VALUES(1,'Administrator','admin')");
        try (var connection =
                        DriverManager.getConnection(
                                settings.url(), settings.username(), settings.password());
                var sql =
                        connection.prepareStatement(
                                "INSERT INTO t_user(id,username,password,status)"
                                    + " VALUES(1,'existing',?,1)")) {
            sql.setString(1, PasswordUtils.encode("existing-password-123"));
            sql.executeUpdate();
        }
        execute("INSERT INTO t_user_role(user_id,role_id) VALUES(1,1)");
        assertEquals(DatabaseInstaller.Classification.LEGACY, installer.check(settings));
        assertEquals(
                "EXISTING_ADMIN_INVALID",
                assertThrows(
                                InstallationException.class,
                                () ->
                                        installer.install(
                                                settings,
                                                "ADOPT",
                                                "installation-a",
                                                Map.of(
                                                        "username",
                                                        "existing",
                                                        "password",
                                                        "incorrect")))
                        .code());
        assertEquals(DatabaseInstaller.Classification.LEGACY, installer.check(settings));
        var credentials = Map.of("username", "existing", "password", "existing-password-123");
        installer.install(settings, "ADOPT", "installation-a", credentials);
        installer.install(settings, "ADOPT", "installation-a", credentials);
        assertEquals(1, count("t_user"));
        assertEquals(1, count("t_role"));
        installer.verifyIdentity(settings, "installation-a");
    }

    @Test
    void administratorWriteFailureRollsBackRolesAndOwnedSchemaCanResume() throws Exception {
        createLegacySchema();
        String checksum;
        try (var stream = new ClassPathResource("db/installation-baseline.sql").getInputStream()) {
            checksum =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stream.readAllBytes()));
        }
        execute(
                "CREATE TABLE ai_installation_migration(installation_id VARCHAR(64) PRIMARY"
                    + " KEY,checksum VARCHAR(64),completed INT)");
        execute(
                "INSERT INTO ai_installation_migration VALUES('installation-a','"
                        + checksum
                        + "',1)");
        execute(
                "CREATE TRIGGER fail_administrator BEFORE INSERT ON t_user CALL"
                    + " 'com.cupk.aiacademy.installation.DatabaseInstallerTest$RejectAdministrator'");
        var admin = Map.of("username", "owner", "password", "long-password-123");
        assertEquals(
                "DATABASE_INSTALLATION_FAILED",
                assertThrows(
                                InstallationException.class,
                                () -> installer.install(settings, "NEW", "installation-a", admin))
                        .code());
        assertEquals(0, count("t_role"));
        assertEquals(0, count("t_user"));
        assertEquals(
                DatabaseInstaller.Classification.EMPTY,
                installer.check(settings, "installation-a"));
        execute("DROP TRIGGER fail_administrator");
        installer.install(settings, "NEW", "installation-a", admin);
        assertEquals(1, count("t_user"));
        assertEquals(3, count("t_role"));
    }

    public static class RejectAdministrator implements org.h2.api.Trigger {
        @Override
        public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow)
                throws java.sql.SQLException {
            throw new java.sql.SQLException("Injected administrator insert failure");
        }
    }

    private void createLegacySchema() throws Exception {
        try (var connection =
                DriverManager.getConnection(
                        settings.url(), settings.username(), settings.password())) {
            ScriptUtils.executeSqlScript(
                    connection, new ClassPathResource("contest-demo-schema.sql"));
        }
    }

    private long count(String table) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                settings.url(), settings.username(), settings.password());
                var sql = connection.createStatement();
                var result = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(String statement) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                settings.url(), settings.username(), settings.password());
                var sql = connection.createStatement()) {
            sql.execute(statement);
        }
    }
}
